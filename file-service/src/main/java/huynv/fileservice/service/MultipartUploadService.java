package huynv.fileservice.service;

import huynv.fileservice.cache.FileCacheService;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileAccessAudit;
import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.domain.MultipartUploadPart;
import huynv.fileservice.domain.MultipartUploadSession;
import huynv.fileservice.domain.MultipartUploadStatus;
import huynv.fileservice.dto.AbortMultipartUploadRequest;
import huynv.fileservice.dto.CompleteMultipartUploadRequest;
import huynv.fileservice.dto.FileMetadataResponse;
import huynv.fileservice.dto.InitiateMultipartUploadRequest;
import huynv.fileservice.dto.InitiateMultipartUploadResponse;
import huynv.fileservice.dto.MultipartCompletedPartRequest;
import huynv.fileservice.dto.MultipartUploadAbortResponse;
import huynv.fileservice.dto.PresignMultipartUploadPartRequest;
import huynv.fileservice.dto.PresignMultipartUploadPartResponse;
import huynv.fileservice.event.FileEventPublisher;
import huynv.fileservice.exception.BadRequestException;
import huynv.fileservice.exception.ConflictException;
import huynv.fileservice.exception.NotFoundException;
import huynv.fileservice.mapper.FileMetadataMapper;
import huynv.fileservice.repository.FileAccessAuditRepository;
import huynv.fileservice.repository.FileRecordRepository;
import huynv.fileservice.repository.MultipartUploadPartRepository;
import huynv.fileservice.repository.MultipartUploadSessionRepository;
import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.storage.MinioStorageService;
import huynv.fileservice.storage.MultipartCompletedPart;
import huynv.fileservice.storage.MultipartUploadInitiation;
import huynv.fileservice.storage.PresignedMultipartUploadPartDetails;
import huynv.fileservice.storage.StorageBucketStrategy;
import huynv.fileservice.storage.StorageObjectKeyFactory;
import huynv.fileservice.validation.FileValidationService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

/**
 * Orchestrates enterprise-grade multipart upload workflows backed by MinIO and tenant-aware persistence.
 */
@Service
public class MultipartUploadService {

    private final FileRecordRepository fileRecordRepository;
    private final MultipartUploadSessionRepository multipartUploadSessionRepository;
    private final MultipartUploadPartRepository multipartUploadPartRepository;
    private final FileAccessAuditRepository fileAccessAuditRepository;
    private final FileValidationService fileValidationService;
    private final StorageObjectKeyFactory storageObjectKeyFactory;
    private final MinioStorageService minioStorageService;
    private final QuotaService quotaService;
    private final FileAuthorizationService fileAuthorizationService;
    private final FileEventPublisher fileEventPublisher;
    private final FileCacheService fileCacheService;
    private final FileMetadataMapper fileMetadataMapper;
    private final ApiIdempotencyService apiIdempotencyService;
    private final FileServiceProperties properties;

    /**
     * Creates a multipart upload service that coordinates session persistence, object storage, and lifecycle transitions.
     *
     * @param fileRecordRepository Repository used for file metadata rows.
     * @param multipartUploadSessionRepository Repository used for multipart upload session rows.
     * @param multipartUploadPartRepository Repository used for multipart part metadata rows.
     * @param fileAccessAuditRepository Repository used for audit rows.
     * @param fileValidationService Validation service used before reserving multipart uploads.
     * @param storageObjectKeyFactory Factory used to generate safe object keys.
     * @param minioStorageService Storage service used for S3-compatible multipart operations.
     * @param quotaService Quota service used to reserve and release tenant storage.
     * @param fileAuthorizationService Authorization service used for centralized access checks.
     * @param fileEventPublisher Event publisher used to enqueue lifecycle events.
     * @param fileCacheService Cache service used to cache file metadata.
     * @param fileMetadataMapper Mapper used to build external DTOs.
     * @param apiIdempotencyService Idempotency service used for protected multipart commands.
     * @param properties File-service properties containing multipart and storage settings.
     */
    public MultipartUploadService(
            FileRecordRepository fileRecordRepository,
            MultipartUploadSessionRepository multipartUploadSessionRepository,
            MultipartUploadPartRepository multipartUploadPartRepository,
            FileAccessAuditRepository fileAccessAuditRepository,
            FileValidationService fileValidationService,
            StorageObjectKeyFactory storageObjectKeyFactory,
            MinioStorageService minioStorageService,
            QuotaService quotaService,
            FileAuthorizationService fileAuthorizationService,
            FileEventPublisher fileEventPublisher,
            FileCacheService fileCacheService,
            FileMetadataMapper fileMetadataMapper,
            ApiIdempotencyService apiIdempotencyService,
            FileServiceProperties properties
    ) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.multipartUploadSessionRepository = Objects.requireNonNull(multipartUploadSessionRepository, "multipartUploadSessionRepository");
        this.multipartUploadPartRepository = Objects.requireNonNull(multipartUploadPartRepository, "multipartUploadPartRepository");
        this.fileAccessAuditRepository = Objects.requireNonNull(fileAccessAuditRepository, "fileAccessAuditRepository");
        this.fileValidationService = Objects.requireNonNull(fileValidationService, "fileValidationService");
        this.storageObjectKeyFactory = Objects.requireNonNull(storageObjectKeyFactory, "storageObjectKeyFactory");
        this.minioStorageService = Objects.requireNonNull(minioStorageService, "minioStorageService");
        this.quotaService = Objects.requireNonNull(quotaService, "quotaService");
        this.fileAuthorizationService = Objects.requireNonNull(fileAuthorizationService, "fileAuthorizationService");
        this.fileEventPublisher = Objects.requireNonNull(fileEventPublisher, "fileEventPublisher");
        this.fileCacheService = Objects.requireNonNull(fileCacheService, "fileCacheService");
        this.fileMetadataMapper = Objects.requireNonNull(fileMetadataMapper, "fileMetadataMapper");
        this.apiIdempotencyService = Objects.requireNonNull(apiIdempotencyService, "apiIdempotencyService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Initiates a native multipart upload and persists the matching control-plane session.
     *
     * @param authentication Current authenticated principal.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload initiation request.
     * @return Returns the initiated multipart upload response.
     */
    public InitiateMultipartUploadResponse initiate(Authentication authentication, String idempotencyKey, InitiateMultipartUploadRequest request) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return apiIdempotencyService.execute(
                user.tenantId(),
                "/files/multipart/initiate",
                idempotencyKey,
                request,
                InitiateMultipartUploadResponse.class,
                () -> initiateInternal(user, request)
        );
    }

    /**
     * Issues a pre-signed upload URL for one multipart upload part.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param partNumber Part number to upload.
     * @param request Multipart part URL request.
     * @return Returns the pre-signed multipart part upload response.
     */
    @Transactional(readOnly = true)
    public PresignMultipartUploadPartResponse presignPart(Authentication authentication, UUID fileId, int partNumber, PresignMultipartUploadPartRequest request) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        MultipartUploadSession session = loadActiveSession(user.tenantId(), fileId);
        if (!Objects.equals(session.getUploadId(), request.uploadId())) {
            throw new ConflictException("INVALID_MULTIPART_UPLOAD_ID", "The supplied multipart upload identifier is invalid.");
        }
        validatePartNumber(partNumber);
        PresignedMultipartUploadPartDetails details = minioStorageService.generatePresignedMultipartUploadPart(
                session.getBucket(),
                session.getObjectKey(),
                session.getUploadId(),
                partNumber,
                properties.getMultipart().getPresignedPartTtl()
        );
        audit(session.getTenantId(), fileId, user.userId(), "MULTIPART_PART_PRESIGN", "SUCCESS", "part=" + partNumber);
        return new PresignMultipartUploadPartResponse(
                session.getFileId(),
                session.getId(),
                session.getUploadId(),
                partNumber,
                details.uploadUrl(),
                details.requiredHeaders(),
                details.expiresAt()
        );
    }

    /**
     * Completes a multipart upload and transitions the reserved file into the pending-scan state.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload completion request.
     * @param correlationId Correlation identifier for the request flow.
     * @param causationId Causation identifier for the request flow.
     * @return Returns the persisted file metadata response.
     */
    public FileMetadataResponse complete(
            Authentication authentication,
            UUID fileId,
            String idempotencyKey,
            CompleteMultipartUploadRequest request,
            String correlationId,
            String causationId
    ) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return apiIdempotencyService.execute(
                user.tenantId(),
                "/files/" + fileId + "/multipart/complete",
                idempotencyKey,
                request,
                FileMetadataResponse.class,
                () -> completeInternal(user, fileId, request, correlationId, causationId)
        );
    }

    /**
     * Aborts an active multipart upload and releases the previously reserved file state.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload abort request.
     * @return Returns the multipart upload abort response.
     */
    public MultipartUploadAbortResponse abort(Authentication authentication, UUID fileId, String idempotencyKey, AbortMultipartUploadRequest request) {
        AuthenticatedUser user = fileAuthorizationService.requireAuthenticated(authentication);
        return apiIdempotencyService.execute(
                user.tenantId(),
                "/files/" + fileId + "/multipart/abort",
                idempotencyKey,
                request,
                MultipartUploadAbortResponse.class,
                () -> abortInternal(user, fileId, request)
        );
    }

    /**
     * Cleans up multipart sessions that have expired before completion.
     *
     * @return Performs side effects by aborting expired multipart uploads and releasing reserved resources.
     */
    @Transactional
    public void cleanupExpiredSessions() {
        for (MultipartUploadSession session : multipartUploadSessionRepository.findByStatusAndExpiresAtBefore(MultipartUploadStatus.INITIATED, Instant.now())) {
            expireSession(session);
        }
    }

    @Transactional
    protected InitiateMultipartUploadResponse initiateInternal(AuthenticatedUser user, InitiateMultipartUploadRequest request) {
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
        MultipartUploadInitiation multipartUploadInitiation = minioStorageService.initiateMultipartUpload(bucket, objectKey, request.contentType());
        Instant expiresAt = Instant.now().plus(properties.getMultipart().getSessionTtl());
        MultipartUploadSession session = new MultipartUploadSession(
                UUID.randomUUID(),
                user.tenantId(),
                user.userId(),
                fileId,
                normalizedCategory,
                bucket,
                objectKey,
                sanitizedFilename,
                request.contentType(),
                request.sizeBytes(),
                request.checksumSha256(),
                request.visibility(),
                request.metadataJson(),
                multipartUploadInitiation.uploadId(),
                expiresAt
        );
        multipartUploadSessionRepository.save(session);
        audit(user.tenantId(), fileId, user.userId(), "MULTIPART_INITIATE", "SUCCESS", null);
        return new InitiateMultipartUploadResponse(fileId, session.getId(), session.getUploadId(), bucket, objectKey, expiresAt);
    }

    @Transactional
    protected FileMetadataResponse completeInternal(AuthenticatedUser user, UUID fileId, CompleteMultipartUploadRequest request, String correlationId, String causationId) {
        MultipartUploadSession session = loadActiveSessionForUpdate(user.tenantId(), fileId);
        if (!Objects.equals(session.getUploadId(), request.uploadId())) {
            throw new ConflictException("INVALID_MULTIPART_UPLOAD_ID", "The supplied multipart upload identifier is invalid.");
        }
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(user.tenantId(), fileId)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        fileAuthorizationService.assertCanMutate(fileRecord, user);
        if (session.getStatus() == MultipartUploadStatus.COMPLETED && (fileRecord.getStatus() == FileStatus.PENDING_SCAN || fileRecord.getStatus() == FileStatus.AVAILABLE)) {
            return fileMetadataMapper.toResponse(fileRecord);
        }
        validateCompletedParts(request.parts());
        fileValidationService.validateChecksum(fileRecord.getChecksumSha256(), request.checksumSha256());
        minioStorageService.completeMultipartUpload(
                session.getBucket(),
                session.getObjectKey(),
                session.getUploadId(),
                request.parts().stream().map(part -> new MultipartCompletedPart(part.partNumber(), part.eTag())).toList()
        );
        persistCompletedParts(session, request.parts());
        fileRecord.markPendingScan(request.metadataJson());
        fileRecordRepository.save(fileRecord);
        session.updateMetadata(request.metadataJson());
        session.markCompleted();
        multipartUploadSessionRepository.save(session);
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String resolvedCausationId = resolveCausationId(causationId, resolvedCorrelationId);
        fileEventPublisher.publishUploaded(fileRecord, resolvedCorrelationId, resolvedCausationId);
        audit(user.tenantId(), fileId, user.userId(), "MULTIPART_COMPLETE", "SUCCESS", null);
        FileMetadataResponse response = fileMetadataMapper.toResponse(fileRecord);
        fileCacheService.putMetadata(user.tenantId(), response);
        return response;
    }

    @Transactional
    protected MultipartUploadAbortResponse abortInternal(AuthenticatedUser user, UUID fileId, AbortMultipartUploadRequest request) {
        MultipartUploadSession session = loadActiveSessionForUpdate(user.tenantId(), fileId);
        if (!Objects.equals(session.getUploadId(), request.uploadId())) {
            throw new ConflictException("INVALID_MULTIPART_UPLOAD_ID", "The supplied multipart upload identifier is invalid.");
        }
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(user.tenantId(), fileId)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "The requested file does not exist."));
        fileAuthorizationService.assertCanMutate(fileRecord, user);
        minioStorageService.abortMultipartUpload(session.getBucket(), session.getObjectKey(), session.getUploadId());
        session.markAborted();
        multipartUploadSessionRepository.save(session);
        if (fileRecord.getStatus() != FileStatus.DELETED) {
            fileRecord.markUploadExpired();
            fileRecord.markDeletePending();
            fileRecord.markDeleted();
            fileRecordRepository.save(fileRecord);
            quotaService.release(user.tenantId(), fileRecord.getSizeBytes());
            fileCacheService.evictMetadata(user.tenantId(), fileId);
        }
        audit(user.tenantId(), fileId, user.userId(), "MULTIPART_ABORT", "SUCCESS", null);
        return new MultipartUploadAbortResponse(fileId, session.getId(), session.getUploadId(), session.getStatus());
    }

    @Transactional
    protected void expireSession(MultipartUploadSession session) {
        minioStorageService.abortMultipartUpload(session.getBucket(), session.getObjectKey(), session.getUploadId());
        session.markExpired();
        multipartUploadSessionRepository.save(session);
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(session.getTenantId(), session.getFileId()).orElse(null);
        if (fileRecord == null || fileRecord.getStatus() == FileStatus.DELETED) {
            return;
        }
        fileRecord.markUploadExpired();
        fileRecord.markDeletePending();
        fileRecord.markDeleted();
        fileRecordRepository.save(fileRecord);
        quotaService.release(session.getTenantId(), fileRecord.getSizeBytes());
        fileCacheService.evictMetadata(session.getTenantId(), fileRecord.getId());
        audit(session.getTenantId(), session.getFileId(), session.getOwnerUserId(), "MULTIPART_EXPIRE", "SUCCESS", null);
    }

    private MultipartUploadSession loadActiveSession(UUID tenantId, UUID fileId) {
        MultipartUploadSession session = multipartUploadSessionRepository.findByTenantIdAndFileIdAndStatus(tenantId, fileId, MultipartUploadStatus.INITIATED)
                .orElseThrow(() -> new NotFoundException("MULTIPART_UPLOAD_NOT_FOUND", "The requested multipart upload session does not exist."));
        if (!session.isActive()) {
            throw new ConflictException("MULTIPART_UPLOAD_EXPIRED", "The multipart upload session has expired.");
        }
        return session;
    }

    private MultipartUploadSession loadActiveSessionForUpdate(UUID tenantId, UUID fileId) {
        MultipartUploadSession session = multipartUploadSessionRepository.findByTenantIdAndFileIdAndStatusForUpdate(tenantId, fileId, MultipartUploadStatus.INITIATED)
                .orElseThrow(() -> new NotFoundException("MULTIPART_UPLOAD_NOT_FOUND", "The requested multipart upload session does not exist."));
        if (!session.isActive()) {
            throw new ConflictException("MULTIPART_UPLOAD_EXPIRED", "The multipart upload session has expired.");
        }
        return session;
    }

    private void validatePartNumber(int partNumber) {
        if (partNumber < 1 || partNumber > properties.getMultipart().getMaxPartCount()) {
            throw new BadRequestException("INVALID_MULTIPART_PART_NUMBER", "The multipart part number is outside the configured range.");
        }
    }

    private void validateCompletedParts(List<MultipartCompletedPartRequest> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new BadRequestException("MULTIPART_PARTS_REQUIRED", "At least one completed multipart part is required.");
        }
        List<MultipartCompletedPartRequest> orderedParts = parts.stream().sorted(Comparator.comparingInt(MultipartCompletedPartRequest::partNumber)).toList();
        int previous = 0;
        for (MultipartCompletedPartRequest part : orderedParts) {
            validatePartNumber(part.partNumber());
            if (part.partNumber() == previous) {
                throw new BadRequestException("DUPLICATE_MULTIPART_PART", "Multipart completion payload contains duplicate part numbers.");
            }
            previous = part.partNumber();
        }
    }

    private void persistCompletedParts(MultipartUploadSession session, List<MultipartCompletedPartRequest> parts) {
        for (MultipartCompletedPartRequest part : parts) {
            MultipartUploadPart persistedPart = multipartUploadPartRepository.findBySessionIdAndPartNumber(session.getId(), part.partNumber())
                    .orElseGet(() -> new MultipartUploadPart(UUID.randomUUID(), session.getId(), part.partNumber(), part.eTag(), part.checksumSha256(), part.sizeBytes()));
            persistedPart.update(part.eTag(), part.checksumSha256(), part.sizeBytes());
            multipartUploadPartRepository.save(persistedPart);
        }
    }

    private void audit(UUID tenantId, UUID fileId, UUID actorUserId, String action, String outcome, String details) {
        fileAccessAuditRepository.save(new FileAccessAudit(tenantId, fileId, actorUserId, action, outcome, details));
    }

    private String resolveBucket(UUID tenantId) {
        if (properties.getStorage().getBucketStrategy() == StorageBucketStrategy.TENANT_ISOLATED) {
            return (properties.getStorage().getBucketPrefix() + "-" + tenantId).toLowerCase();
        }
        return properties.getStorage().getSharedBucket();
    }

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

    private String resolveCausationId(String causationId, String correlationId) {
        if (causationId != null && !causationId.isBlank()) {
            return causationId;
        }
        return correlationId;
    }
}


