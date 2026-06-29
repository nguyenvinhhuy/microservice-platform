package huynv.fileservice.service;

import huynv.event.BaseEvent;
import huynv.event.file.FileScanCompletedEvent;
import huynv.fileservice.cache.FileCacheService;
import huynv.fileservice.cache.PresignedUploadState;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileAccessAudit;
import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.domain.FileVisibility;
import huynv.fileservice.domain.MalwareScanStatus;
import huynv.fileservice.dto.ConfirmUploadRequest;
import huynv.fileservice.dto.DownloadTicketResponse;
import huynv.fileservice.dto.FileMetadataResponse;
import huynv.fileservice.dto.PresignedDownloadResponse;
import huynv.fileservice.dto.PresignedUploadRequest;
import huynv.fileservice.dto.PresignedUploadResponse;
import huynv.fileservice.event.FileEventPublisher;
import huynv.fileservice.exception.BadRequestException;
import huynv.fileservice.exception.ConflictException;
import huynv.fileservice.exception.NotFoundException;
import huynv.fileservice.mapper.FileMetadataMapper;
import huynv.fileservice.metrics.FileMetrics;
import huynv.fileservice.repository.FileAccessAuditRepository;
import huynv.fileservice.repository.FileRecordRepository;
import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.security.JwtUserContextExtractor;
import huynv.fileservice.storage.DownloadedObject;
import huynv.fileservice.storage.MinioStorageService;
import huynv.fileservice.storage.PresignedDownloadDetails;
import huynv.fileservice.storage.PresignedUploadDetails;
import huynv.fileservice.storage.StorageBucketStrategy;
import huynv.fileservice.storage.StorageObjectKeyFactory;
import huynv.fileservice.validation.FileValidationService;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the file lifecycle use cases spanning validation, storage, quotas, auditing, caching, and event publishing.
 */
@Service
public class FileLifecycleService {

    private final FileRecordRepository fileRecordRepository;
    private final FileAccessAuditRepository fileAccessAuditRepository;
    private final FileMetadataMapper fileMetadataMapper;
    private final FileValidationService fileValidationService;
    private final StorageObjectKeyFactory storageObjectKeyFactory;
    private final MinioStorageService minioStorageService;
    private final QuotaService quotaService;
    private final FileAuthorizationService fileAuthorizationService;
    private final FileEventPublisher fileEventPublisher;
    private final FileCacheService fileCacheService;
    private final FileMetrics fileMetrics;
    private final ApiIdempotencyService apiIdempotencyService;
    private final FileServiceProperties properties;
    private final JwtUserContextExtractor jwtUserContextExtractor;
    private final DownloadTicketService downloadTicketService;

    /**
     * Creates the main lifecycle orchestration service for file-service commands and queries.
     *
     * @param fileRecordRepository Repository used for file metadata rows.
     * @param fileAccessAuditRepository Repository used for audit rows.
     * @param fileMetadataMapper Mapper used to build external DTOs.
     * @param fileValidationService Validation service used before storage operations.
     * @param storageObjectKeyFactory Factory used to generate safe object keys.
     * @param minioStorageService Storage service used for MinIO-compatible operations.
     * @param quotaService Quota service used to reserve and release tenant storage.
     * @param fileAuthorizationService Authorization service used for centralized access checks.
     * @param fileEventPublisher Event publisher used to enqueue lifecycle events.
     * @param fileCacheService Cache service used to cache metadata and upload state.
     * @param fileMetrics Metrics service used to record operational counters and timers.
     * @param apiIdempotencyService Idempotency service used for protected write commands.
     * @param properties File-service properties containing runtime behavior.
     * @param jwtUserContextExtractor JWT context extractor used for optional audit identity.
     * @param downloadTicketService Download-ticket service used for short-lived download grants.
     */
    public FileLifecycleService(
            FileRecordRepository fileRecordRepository,
            FileAccessAuditRepository fileAccessAuditRepository,
            FileMetadataMapper fileMetadataMapper,
            FileValidationService fileValidationService,
            StorageObjectKeyFactory storageObjectKeyFactory,
            MinioStorageService minioStorageService,
            QuotaService quotaService,
            FileAuthorizationService fileAuthorizationService,
            FileEventPublisher fileEventPublisher,
            FileCacheService fileCacheService,
            FileMetrics fileMetrics,
            ApiIdempotencyService apiIdempotencyService,
            FileServiceProperties properties,
            JwtUserContextExtractor jwtUserContextExtractor,
            DownloadTicketService downloadTicketService
    ) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.fileAccessAuditRepository = Objects.requireNonNull(fileAccessAuditRepository, "fileAccessAuditRepository");
        this.fileMetadataMapper = Objects.requireNonNull(fileMetadataMapper, "fileMetadataMapper");
        this.fileValidationService = Objects.requireNonNull(fileValidationService, "fileValidationService");
        this.storageObjectKeyFactory = Objects.requireNonNull(storageObjectKeyFactory, "storageObjectKeyFactory");
        this.minioStorageService = Objects.requireNonNull(minioStorageService, "minioStorageService");
        this.quotaService = Objects.requireNonNull(quotaService, "quotaService");
        this.fileAuthorizationService = Objects.requireNonNull(fileAuthorizationService, "fileAuthorizationService");
        this.fileEventPublisher = Objects.requireNonNull(fileEventPublisher, "fileEventPublisher");
        this.fileCacheService = Objects.requireNonNull(fileCacheService, "fileCacheService");
        this.fileMetrics = Objects.requireNonNull(fileMetrics, "fileMetrics");
        this.apiIdempotencyService = Objects.requireNonNull(apiIdempotencyService, "apiIdempotencyService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
        this.downloadTicketService = Objects.requireNonNull(downloadTicketService, "downloadTicketService");
    }

    /**
     * Performs a direct multipart upload through file-service and persists the resulting metadata record.
     *
     * @param authentication Current authenticated principal.
     * @param multipartFile Uploaded multipart file.
     * @param category Business category for the file.
     * @param visibility Visibility mode for later reads.
     * @param metadataJson Optional metadata payload.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the persisted file metadata response.
     */
    @Transactional
    public FileMetadataResponse uploadDirect(
            Authentication authentication,
            MultipartFile multipartFile,
            String category,
            FileVisibility visibility,
            String metadataJson,
            String correlationId,
            String causationId
    ) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        fileValidationService.validateMultipartUpload(multipartFile, category);
        String sanitizedFilename = fileValidationService.sanitizeFilename(multipartFile.getOriginalFilename());
        String normalizedCategory = fileValidationService.normalizeCategory(category);
        String extension = fileValidationService.extractExtension(sanitizedFilename);
        UUID fileId = UUID.randomUUID();
        String bucket = resolveBucket(user.tenantId());
        String objectKey = storageObjectKeyFactory.createObjectKey(user.tenantId(), normalizedCategory, fileId, extension);
        Path temporaryFile = writeMultipartToTempFile(multipartFile, extension);
        long startNanos = System.nanoTime();
        try {
            String checksum = computeSha256(temporaryFile);
            minioStorageService.upload(bucket, objectKey, temporaryFile, multipartFile.getContentType());
            quotaService.reserve(user.tenantId(), multipartFile.getSize());
            FileRecord fileRecord = new FileRecord(
                    fileId,
                    user.tenantId(),
                    user.userId(),
                    normalizedCategory,
                    objectKey,
                    bucket,
                    sanitizedFilename,
                    multipartFile.getContentType(),
                    multipartFile.getSize(),
                    checksum,
                    visibility,
                    metadataJson,
                    null,
                    properties.getStorage().getEncryptionMode(),
                    properties.getStorage().getEncryptionKeyReference()
            );
            fileRecord.markPendingScan(metadataJson);
            fileRecordRepository.save(fileRecord);
            audit(user.tenantId(), fileId, user.userId(), "DIRECT_UPLOAD", "SUCCESS", null);
            String resolvedCorrelationId = resolveCorrelationId(correlationId);
            String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
            fileEventPublisher.publishUploaded(fileRecord, resolvedCorrelationId, resolvedCausationId);
            FileMetadataResponse response = fileMetadataMapper.toResponse(fileRecord);
            fileCacheService.putMetadata(user.tenantId(), response);
            fileMetrics.recordUploadSuccess(multipartFile.getSize(), System.nanoTime() - startNanos);
            return response;
        } catch (RuntimeException ex) {
            safeDeleteObject(bucket, objectKey);
            fileMetrics.recordUploadFailure();
            throw ex;
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * Creates a pre-signed upload reservation and persists the pending metadata row.
     *
     * @param authentication Current authenticated principal.
     * @param request Client request describing the future upload.
     * @return Returns the reservation response containing the upload URL and token.
     */
    @Transactional
    public PresignedUploadResponse createPresignedUpload(Authentication authentication, PresignedUploadRequest request) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        fileValidationService.validateUploadPolicy(request.originalFilename(), request.contentType(), request.sizeBytes());
        String sanitizedFilename = fileValidationService.sanitizeFilename(request.originalFilename());
        String normalizedCategory = fileValidationService.normalizeCategory(request.category());
        String extension = fileValidationService.extractExtension(sanitizedFilename);
        UUID fileId = UUID.randomUUID();
        String bucket = resolveBucket(user.tenantId());
        String objectKey = storageObjectKeyFactory.createObjectKey(user.tenantId(), normalizedCategory, fileId, extension);
        quotaService.reserve(user.tenantId(), request.sizeBytes());
        FileRecord fileRecord = new FileRecord(
                fileId,
                user.tenantId(),
                user.userId(),
                normalizedCategory,
                objectKey,
                bucket,
                sanitizedFilename,
                request.contentType(),
                request.sizeBytes(),
                request.checksumSha256(),
                request.visibility(),
                request.metadataJson(),
                null,
                properties.getStorage().getEncryptionMode(),
                properties.getStorage().getEncryptionKeyReference()
        );
        fileRecordRepository.save(fileRecord);
        String uploadToken = UUID.randomUUID().toString();
        PresignedUploadDetails presignedUploadDetails = minioStorageService.generatePresignedUpload(
                bucket,
                objectKey,
                request.contentType(),
                properties.getStorage().getPresignedUploadTtl()
        );
        fileCacheService.putPresignedState(uploadToken, new PresignedUploadState(
                fileId,
                user.tenantId(),
                bucket,
                objectKey,
                request.checksumSha256(),
                presignedUploadDetails.expiresAt()
        ));
        audit(user.tenantId(), fileId, user.userId(), "PRESIGN_UPLOAD", "SUCCESS", null);
        fileMetrics.recordPresignedUrl();
        return new PresignedUploadResponse(
                fileId,
                uploadToken,
                bucket,
                objectKey,
                presignedUploadDetails.uploadUrl(),
                presignedUploadDetails.requiredHeaders(),
                presignedUploadDetails.expiresAt()
        );
    }

    /**
     * Confirms a previously reserved pre-signed upload using PostgreSQL-backed idempotency protection.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier being confirmed.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Confirmation request payload.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the confirmed file metadata response.
     */
    public FileMetadataResponse confirmUpload(
            Authentication authentication,
            UUID fileId,
            String idempotencyKey,
            ConfirmUploadRequest request,
            String correlationId,
            String causationId
    ) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return apiIdempotencyService.execute(
                user.tenantId(),
                "/files/" + fileId + "/confirm",
                idempotencyKey,
                request,
                FileMetadataResponse.class,
                () -> confirmUploadInternal(user, fileId, request, correlationId, causationId)
        );
    }

    /**
     * Loads file metadata for the current principal or for an anonymous public read.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the file metadata response.
     */
    @Transactional(readOnly = true)
    public FileMetadataResponse getMetadata(Authentication authentication, UUID fileId) {
        FileMetadataResponse cached = loadCachedMetadata(authentication, fileId);
        if (cached != null) {
            return cached;
        }
        FileRecord fileRecord = loadFileForRead(authentication, fileId);
        FileMetadataResponse response = fileMetadataMapper.toResponse(fileRecord);
        fileCacheService.putMetadata(fileRecord.getTenantId(), response);
        return response;
    }

    /**
     * Lists active files for the authenticated tenant.
     *
     * @param authentication Current authenticated principal.
     * @param pageable Requested page.
     * @return Returns a page of tenant-owned file metadata responses.
     */
    @Transactional(readOnly = true)
    public Page<FileMetadataResponse> list(Authentication authentication, Pageable pageable) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return fileRecordRepository.findActiveByTenantId(user.tenantId(), pageable).map(fileMetadataMapper::toResponse);
    }

    /**
     * Generates a pre-signed download URL when the caller is allowed to read the file.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the pre-signed download response.
     */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse createPresignedDownload(Authentication authentication, UUID fileId) {
        FileRecord fileRecord = loadAvailableFileForRead(authentication, fileId);
        long startNanos = System.nanoTime();
        PresignedDownloadDetails details = minioStorageService.generatePresignedDownload(
                fileRecord.getBucket(),
                fileRecord.getObjectKey(),
                properties.getStorage().getPresignedDownloadTtl()
        );
        fileMetrics.recordMinioLatency(System.nanoTime() - startNanos);
        fileMetrics.recordPresignedUrl();
        audit(fileRecord.getTenantId(), fileRecord.getId(), jwtUserContextExtractor.tryExtractUserId(authentication), "PRESIGN_DOWNLOAD", "SUCCESS", null);
        return new PresignedDownloadResponse(fileRecord.getId(), details.downloadUrl(), details.expiresAt());
    }

    /**
     * Issues a short-lived download ticket for an available file that the caller is allowed to read.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @param singleUse Whether the issued ticket may be redeemed only once.
     * @return Returns the issued opaque download ticket.
     */
    @Transactional
    public DownloadTicketResponse createDownloadTicket(Authentication authentication, UUID fileId, boolean singleUse) {
        FileRecord fileRecord = loadAvailableFileForRead(authentication, fileId);
        Instant expiresAt = Instant.now().plus(properties.getStorage().getDownloadTicketTtl());
        String token = downloadTicketService.issue(fileRecord.getTenantId(), authentication, fileId, expiresAt, singleUse);
        audit(fileRecord.getTenantId(), fileRecord.getId(), jwtUserContextExtractor.tryExtractUserId(authentication), "ISSUE_DOWNLOAD_TICKET", "SUCCESS", singleUse ? "single-use" : "multi-use");
        return new DownloadTicketResponse(fileRecord.getId(), token, expiresAt, singleUse);
    }

    /**
     * Streams a file download when the caller is allowed to read the file.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns a streaming HTTP response for the object bytes.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> download(Authentication authentication, UUID fileId) {
        FileRecord fileRecord = loadAvailableFileForRead(authentication, fileId);
        return buildDownloadResponse(fileRecord, authentication, "DOWNLOAD");
    }

    /**
     * Streams a file download after a valid short-lived download ticket has been redeemed.
     *
     * @param authentication Current principal, which may be null for public ticket redemption.
     * @param token Opaque download ticket token.
     * @return Returns a streaming HTTP response for the object bytes.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> downloadByTicket(Authentication authentication, String token) {
        huynv.fileservice.domain.DownloadTicket downloadTicket = downloadTicketService.redeem(token, authentication);
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndId(downloadTicket.getTenantId(), downloadTicket.getFileId())
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        if (fileRecord.getStatus() != FileStatus.AVAILABLE) {
            throw new ConflictException("FILE_NOT_AVAILABLE", "The requested file is not available for reading.");
        }
        return buildDownloadResponse(fileRecord, authentication, "DOWNLOAD_TICKET_REDEEM");
    }

    /**
     * Builds the common streaming response used by direct and ticket-backed downloads.
     *
     * @param fileRecord File metadata record for the object being downloaded.
     * @param authentication Current principal, which may be null for public reads.
     * @param auditAction Audit action name recorded for the download.
     * @return Returns a streaming HTTP response for the object bytes.
     */
    private ResponseEntity<InputStreamResource> buildDownloadResponse(FileRecord fileRecord, Authentication authentication, String auditAction) {
        long startNanos = System.nanoTime();
        DownloadedObject downloadedObject = minioStorageService.download(fileRecord.getBucket(), fileRecord.getObjectKey());
        fileMetrics.recordMinioLatency(System.nanoTime() - startNanos);
        fileMetrics.recordDownload();
        audit(fileRecord.getTenantId(), fileRecord.getId(), jwtUserContextExtractor.tryExtractUserId(authentication), auditAction, "SUCCESS", null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileRecord.getOriginalFilename()).build().toString())
                .contentType(MediaType.parseMediaType(Optional.ofNullable(downloadedObject.contentType()).orElse(fileRecord.getContentType())))
                .contentLength(downloadedObject.contentLength())
                .body(new InputStreamResource(downloadedObject.inputStream()));
    }

    /**
     * Soft-deletes a file using PostgreSQL-backed idempotency protection.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier being deleted.
     * @param idempotencyKey Client-provided idempotency key.
     * @param reason Optional human-readable deletion reason.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the deleted file metadata response.
     */
    public FileMetadataResponse delete(
            Authentication authentication,
            UUID fileId,
            String idempotencyKey,
            String reason,
            String correlationId,
            String causationId
    ) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return apiIdempotencyService.execute(
                user.tenantId(),
                "/files/" + fileId,
                idempotencyKey,
                Map.of("reason", reason == null ? "" : reason),
                FileMetadataResponse.class,
                () -> deleteInternal(user, fileId, reason, correlationId, causationId)
        );
    }

    /**
     * Applies an asynchronous scan result event to the affected file record.
     *
     * @param event Scan result event envelope.
     */
    @Transactional
    public void applyScanResult(BaseEvent<FileScanCompletedEvent> event) {
        Objects.requireNonNull(event, "event");
        FileScanCompletedEvent payload = Objects.requireNonNull(event.data(), "event.data");
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(payload.tenantId(), payload.fileId())
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The referenced file does not exist for the tenant."));
        if (fileRecord.getStatus() == FileStatus.DELETED) {
            return;
        }
        putMdc(fileRecord);
        try {
            if (payload.malwareStatus() == null) {
                throw new BadRequestException("INVALID_SCAN_STATUS", "The scan result is missing a malware status.");
            }
            MalwareScanStatus malwareScanStatus = MalwareScanStatus.valueOf(payload.malwareStatus());
            if (malwareScanStatus == MalwareScanStatus.CLEAN) {
                fileRecord.markAvailable();
                fileEventPublisher.publishAvailable(fileRecord, event.correlationId(), event.eventId());
                audit(fileRecord.getTenantId(), fileRecord.getId(), null, "SCAN_RESULT", "AVAILABLE", payload.reason());
            } else {
                fileRecord.markQuarantined(malwareScanStatus);
                fileEventPublisher.publishQuarantined(fileRecord, payload.reason(), event.correlationId(), event.eventId());
                audit(fileRecord.getTenantId(), fileRecord.getId(), null, "SCAN_RESULT", "QUARANTINED", payload.reason());
            }
            fileRecordRepository.save(fileRecord);
            fileCacheService.putMetadata(fileRecord.getTenantId(), fileMetadataMapper.toResponse(fileRecord));
        } catch (RuntimeException ex) {
            fileMetrics.recordScanFailure();
            throw ex;
        } finally {
            clearFileMdc();
        }
    }

    /**
     * Cleans up stale pending uploads so reserved quota is eventually released.
     */
    @Transactional
    public void cleanupStalePendingUploads() {
        Instant threshold = Instant.now().minus(properties.getQuota().getPendingUploadTtl());
        for (FileRecord fileRecord : fileRecordRepository.findByStatusAndCreatedAtBefore(FileStatus.PENDING_UPLOAD, threshold)) {
            fileRecord.markUploadExpired();
            fileRecord.markDeletePending();
            fileRecord.markDeleted();
            fileRecordRepository.save(fileRecord);
            quotaService.release(fileRecord.getTenantId(), fileRecord.getSizeBytes());
            audit(fileRecord.getTenantId(), fileRecord.getId(), null, "PENDING_UPLOAD_CLEANUP", "SUCCESS", "Expired pending upload cleaned up.");
            fileCacheService.evictMetadata(fileRecord.getTenantId(), fileRecord.getId());
            safeDeleteObject(fileRecord.getBucket(), fileRecord.getObjectKey());
        }
    }

    /**
     * Confirms a reserved pre-signed upload after idempotency protection has admitted execution.
     *
     * @param user Authenticated tenant user.
     * @param fileId File identifier being confirmed.
     * @param request Confirmation request payload.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the confirmed file metadata response.
     */
    protected FileMetadataResponse confirmUploadInternal(
            AuthenticatedUser user,
            UUID fileId,
            ConfirmUploadRequest request,
            String correlationId,
            String causationId
    ) {
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(user.tenantId(), fileId)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        fileAuthorizationService.assertCanMutate(fileRecord, user);
        if (fileRecord.getStatus() == FileStatus.PENDING_SCAN || fileRecord.getStatus() == FileStatus.AVAILABLE) {
            return fileMetadataMapper.toResponse(fileRecord);
        }
        if (fileRecord.getStatus() != FileStatus.PENDING_UPLOAD) {
            throw new ConflictException("INVALID_FILE_STATE", "Only pending uploads can be confirmed.");
        }
        PresignedUploadState presignedUploadState = fileCacheService.getPresignedState(request.uploadToken());
        if (presignedUploadState == null || !fileId.equals(presignedUploadState.fileId()) || !user.tenantId().equals(presignedUploadState.tenantId())) {
            throw new ConflictException("INVALID_UPLOAD_TOKEN", "The supplied upload token is invalid or expired.");
        }
        if (presignedUploadState.expiresAt().isBefore(Instant.now())) {
            fileCacheService.evictPresignedState(request.uploadToken());
            throw new ConflictException("UPLOAD_TOKEN_EXPIRED", "The supplied upload token has expired.");
        }
        fileValidationService.validateChecksum(presignedUploadState.expectedChecksumSha256(), request.checksumSha256());
        minioStorageService.statObject(fileRecord.getBucket(), fileRecord.getObjectKey());
        fileValidationService.validateChecksum(fileRecord.getChecksumSha256(), request.checksumSha256());
        fileRecord.markPendingScan(request.metadataJson());
        fileRecordRepository.save(fileRecord);
        fileCacheService.evictPresignedState(request.uploadToken());
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        fileEventPublisher.publishUploaded(fileRecord, resolvedCorrelationId, resolvedCausationId);
        audit(user.tenantId(), fileId, user.userId(), "CONFIRM_UPLOAD", "SUCCESS", null);
        FileMetadataResponse response = fileMetadataMapper.toResponse(fileRecord);
        fileCacheService.putMetadata(user.tenantId(), response);
        return response;
    }

    /**
     * Deletes a file after idempotency protection has admitted execution.
     *
     * @param user Authenticated tenant user.
     * @param fileId File identifier being deleted.
     * @param reason Optional human-readable deletion reason.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the deleted file metadata response.
     */
    protected FileMetadataResponse deleteInternal(
            AuthenticatedUser user,
            UUID fileId,
            String reason,
            String correlationId,
            String causationId
    ) {
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(user.tenantId(), fileId)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        fileAuthorizationService.assertCanMutate(fileRecord, user);
        if (fileRecord.getStatus() == FileStatus.DELETED) {
            return fileMetadataMapper.toResponse(fileRecord);
        }
        if (fileRecord.getStatus() != FileStatus.DELETE_PENDING) {
            fileRecord.markDeletePending();
        }
        fileRecord.markDeleted();
        fileRecordRepository.save(fileRecord);
        quotaService.release(user.tenantId(), fileRecord.getSizeBytes());
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        fileEventPublisher.publishDeleted(fileRecord, resolvedCorrelationId, resolvedCausationId);
        audit(user.tenantId(), fileId, user.userId(), "DELETE", "SUCCESS", reason);
        registerDeleteAfterCommit(fileRecord.getBucket(), fileRecord.getObjectKey());
        fileCacheService.evictMetadata(user.tenantId(), fileId);
        return fileMetadataMapper.toResponse(fileRecord);
    }

    /**
     * Loads a readable file record and enforces authorization.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the file record after authorization succeeds.
     */
    @Transactional(readOnly = true)
    protected FileRecord loadFileForRead(Authentication authentication, UUID fileId) {
        FileRecord fileRecord = findFileByVisibilityContext(authentication, fileId);
        fileAuthorizationService.assertCanRead(fileRecord, authentication);
        if (fileRecord.getStatus() == FileStatus.DELETED) {
            throw new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist.");
        }
        return fileRecord;
    }

    /**
     * Loads a readable file record and enforces that it is available for downloads.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the available file record after authorization succeeds.
     */
    @Transactional(readOnly = true)
    protected FileRecord loadAvailableFileForRead(Authentication authentication, UUID fileId) {
        FileRecord fileRecord = loadFileForRead(authentication, fileId);
        if (fileRecord.getStatus() != FileStatus.AVAILABLE) {
            throw new ConflictException("FILE_NOT_AVAILABLE", "The requested file is not available for reading.");
        }
        return fileRecord;
    }

    /**
     * Tries to resolve file metadata from cache when the caller identity provides a tenant cache key.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns cached metadata when available, or null otherwise.
     */
    private FileMetadataResponse loadCachedMetadata(Authentication authentication, UUID fileId) {
        if (authentication == null) {
            return null;
        }
        AuthenticatedUser user = jwtUserContextExtractor.extract(authentication);
        return fileCacheService.getMetadata(user.tenantId(), fileId);
    }

    /**
     * Resolves the correct file lookup strategy depending on whether the read is authenticated or anonymous.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the matching file record.
     */
    private FileRecord findFileByVisibilityContext(Authentication authentication, UUID fileId) {
        if (authentication == null) {
            return fileRecordRepository.findById(fileId)
                    .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        }
        AuthenticatedUser user = jwtUserContextExtractor.extract(authentication);
        return fileRecordRepository.findByTenantIdAndId(user.tenantId(), fileId)
                .orElseGet(() -> fileRecordRepository.findById(fileId)
                        .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist.")));
    }

    /**
     * Resolves the storage bucket name for the supplied tenant.
     *
     * @param tenantId Tenant identifier.
     * @return Returns the storage bucket name.
     */
    private String resolveBucket(UUID tenantId) {
        if (properties.getStorage().getBucketStrategy() == StorageBucketStrategy.TENANT_ISOLATED) {
            return (properties.getStorage().getBucketPrefix() + "-" + tenantId).toLowerCase();
        }
        return properties.getStorage().getSharedBucket();
    }

    /**
     * Writes a multipart upload into a temporary file so hashing and S3 upload can stream from disk.
     *
     * @param multipartFile Uploaded multipart file.
     * @param extension Sanitized file extension.
     * @return Returns the temporary file path.
     */
    private Path writeMultipartToTempFile(MultipartFile multipartFile, String extension) {
        try {
            Path temporaryFile = Files.createTempFile("file-service-upload-", "." + extension);
            multipartFile.transferTo(temporaryFile);
            return temporaryFile;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to stage the multipart file to temporary storage.", ex);
        }
    }

    /**
     * Computes the SHA-256 checksum of a temporary file by streaming bytes from disk.
     *
     * @param filePath Temporary file path.
     * @return Returns the lowercase hexadecimal SHA-256 checksum.
     */
    private String computeSha256(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute the file checksum.", ex);
        }
    }

    /**
     * Removes a temporary upload file after hashing and upload work has completed.
     *
     * @param filePath Temporary file path.
     */
    private void deleteTemporaryFile(Path filePath) {
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Temporary file cleanup is best-effort.
        }
    }

    /**
     * Registers an object deletion callback that executes only after the surrounding transaction commits.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Storage object key.
     */
    private void registerDeleteAfterCommit(String bucket, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDeleteObject(bucket, objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * Deletes the storage object after the surrounding transaction commits successfully.
             */
            @Override
            public void afterCommit() {
                safeDeleteObject(bucket, objectKey);
            }
        });
    }

    /**
     * Attempts to delete a storage object without masking the main business result when deletion fails.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Storage object key.
     */
    private void safeDeleteObject(String bucket, String objectKey) {
        try {
            minioStorageService.deleteObject(bucket, objectKey);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup after business state is already durable.
        }
    }

    /**
     * Persists an audit row for a file action.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param actorUserId Optional acting user identifier.
     * @param action Audited action name.
     * @param outcome Audited outcome name.
     * @param details Optional human-readable details.
     */
    private void audit(UUID tenantId, UUID fileId, UUID actorUserId, String action, String outcome, String details) {
        fileAccessAuditRepository.save(new FileAccessAudit(tenantId, fileId, actorUserId, action, outcome, details));
    }

    /**
     * Resolves a correlation identifier from the request or creates a fallback value.
     *
     * @param correlationId Requested correlation identifier.
     * @return Returns a usable correlation identifier.
     */
    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String mdcCorrelationId = MDC.get("correlationId");
        if (mdcCorrelationId != null && !mdcCorrelationId.isBlank()) {
            return mdcCorrelationId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Resolves a causation identifier from the request or falls back to the correlation identifier.
     *
     * @param causationId Requested causation identifier.
     * @param correlationId Resolved correlation identifier.
     * @return Returns a usable causation identifier.
     */
    private String resolveCausationId(String causationId, String correlationId) {
        if (causationId != null && !causationId.isBlank()) {
            return causationId;
        }
        return correlationId;
    }

    /**
     * Places file identifiers into MDC for better structured logs during event-driven state transitions.
     *
     * @param fileRecord File metadata record.
     */
    private void putMdc(FileRecord fileRecord) {
        MDC.put("tenantId", fileRecord.getTenantId().toString());
        MDC.put("fileId", fileRecord.getId().toString());
        MDC.put("userId", fileRecord.getOwnerUserId().toString());
    }

    /**
     * Clears file-specific MDC keys after a lifecycle transition completes.
     */
    private void clearFileMdc() {
        MDC.remove("tenantId");
        MDC.remove("fileId");
        MDC.remove("userId");
    }

}

