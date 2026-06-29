package huynv.fileservice.event;

import huynv.event.BaseEvent;
import huynv.event.file.FileUploadedEvent;
import huynv.fileservice.domain.ChecksumBlacklistEntry;
import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.domain.MalwareScanStatus;
import huynv.fileservice.metrics.FileMetrics;
import huynv.fileservice.repository.ChecksumBlacklistRepository;
import huynv.fileservice.repository.FileRecordRepository;
import huynv.fileservice.storage.DownloadedObject;
import huynv.fileservice.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Executes malware scans for uploaded files and publishes the resulting scan-completed events.
 */
@Service
public class ScanResultHandler {

    private final FileRecordRepository fileRecordRepository;
    private final MinioStorageService minioStorageService;
    private final MalwareScanner malwareScanner;
    private final FileEventPublisher fileEventPublisher;
    private final ChecksumBlacklistRepository checksumBlacklistRepository;
    private final FileMetrics fileMetrics;

    /**
     * Creates a scan-result handler that reuses shared storage and outbox infrastructure.
     *
     * @param fileRecordRepository Repository used to load and update file records.
     * @param minioStorageService Storage service used to stream object bytes for scanning.
     * @param malwareScanner Scanner used to produce malware verdicts.
     * @param fileEventPublisher Event publisher used to enqueue scan-completed events.
     * @param checksumBlacklistRepository Repository used to persist malicious checksums.
     * @param fileMetrics Metrics recorder used for scan observability.
     * @return Initializes the scan-result handler.
     */
    public ScanResultHandler(
            FileRecordRepository fileRecordRepository,
            MinioStorageService minioStorageService,
            MalwareScanner malwareScanner,
            FileEventPublisher fileEventPublisher,
            ChecksumBlacklistRepository checksumBlacklistRepository,
            FileMetrics fileMetrics
    ) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.minioStorageService = Objects.requireNonNull(minioStorageService, "minioStorageService");
        this.malwareScanner = Objects.requireNonNull(malwareScanner, "malwareScanner");
        this.fileEventPublisher = Objects.requireNonNull(fileEventPublisher, "fileEventPublisher");
        this.checksumBlacklistRepository = Objects.requireNonNull(checksumBlacklistRepository, "checksumBlacklistRepository");
        this.fileMetrics = Objects.requireNonNull(fileMetrics, "fileMetrics");
    }

    /**
     * Scans the uploaded file referenced by the supplied event and publishes a scan-completed event.
     *
     * @param event Uploaded-file event envelope.
     * @return Performs a side effect by publishing a scan result or recording a retryable failure.
     */
    public void handleUploadedEvent(BaseEvent<FileUploadedEvent> event) {
        Objects.requireNonNull(event, "event");
        FileUploadedEvent payload = Objects.requireNonNull(event.data(), "event.data");
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndId(payload.tenantId(), payload.fileId()).orElse(null);
        if (fileRecord == null || fileRecord.getStatus() == FileStatus.DELETED || fileRecord.getStatus() == FileStatus.ARCHIVED) {
            return;
        }
        long startedAt = System.nanoTime();
        DownloadedObject downloadedObject = minioStorageService.download(fileRecord.getBucket(), fileRecord.getObjectKey());
        try (java.io.InputStream inputStream = downloadedObject.inputStream()) {
            registerScanAttempt(fileRecord.getTenantId(), fileRecord.getId());
            ScanVerdict verdict = malwareScanner.scan(fileRecord, inputStream);
            long scanDurationMs = Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            if (verdict.malwareScanStatus() == MalwareScanStatus.INFECTED) {
                checksumBlacklistRepository.save(new ChecksumBlacklistEntry(
                        fileRecord.getChecksumSha256(),
                        fileRecord.getTenantId(),
                        verdict.reason(),
                        malwareScanner.scannerName(),
                        null
                ));
                fileMetrics.recordScanInfected();
            }
            if (verdict.timedOut()) {
                fileMetrics.recordScanTimeout();
            }
            fileMetrics.recordScanDuration(System.nanoTime() - startedAt);
            publishScanCompleted(fileRecord.getTenantId(), fileRecord.getId(), verdict, scanDurationMs, event.correlationId(), event.eventId());
        } catch (Exception ex) {
            markScanFailed(fileRecord.getTenantId(), fileRecord.getId(), ex.getMessage());
            fileMetrics.recordScanFailure();
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to complete the malware scan workflow.", ex);
        }
    }

    /**
     * Retries a previously scan-failed file by synthesizing a minimal uploaded event context.
     *
     * @param fileRecord File metadata record eligible for retry.
     * @return Performs a side effect by re-running the scan flow.
     */
    public void retry(FileRecord fileRecord) {
        Objects.requireNonNull(fileRecord, "fileRecord");
        handleUploadedEvent(new BaseEvent<>(
                "retry-" + fileRecord.getId(),
                "file.uploaded.v1",
                "file-service",
                Instant.now(),
                fileRecord.getId().toString(),
                fileRecord.getVersion(),
                "file.uploaded.v1",
                null,
                "retry-" + fileRecord.getId(),
                "retry-" + fileRecord.getId(),
                new FileUploadedEvent(
                        fileRecord.getId(),
                        fileRecord.getTenantId(),
                        fileRecord.getOwnerUserId(),
                        fileRecord.getCategory(),
                        fileRecord.getBucket(),
                        fileRecord.getObjectKey(),
                        fileRecord.getOriginalFilename(),
                        fileRecord.getContentType(),
                        fileRecord.getSizeBytes(),
                        fileRecord.getChecksumSha256(),
                        fileRecord.getVisibility().name(),
                        Instant.now()
                )
        ));
    }

    /**
     * Persists a scan-attempt marker before streaming bytes to the scanner.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @return Performs a side effect by updating the last-scan-attempt timestamp.
     */
    @Transactional
    public void registerScanAttempt(java.util.UUID tenantId, java.util.UUID fileId) {
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(tenantId, fileId).orElseThrow();
        fileRecord.markScanAttemptStarted();
        fileRecordRepository.save(fileRecord);
    }

    /**
     * Publishes the durable scan-completed event after a successful scanner verdict.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param verdict Malware verdict returned by the scanner.
     * @param scanDurationMs Scan duration in milliseconds.
     * @param correlationId Correlation identifier.
     * @param causationId Causation identifier.
     * @return Performs a side effect by enqueueing a scan-completed event.
     */
    @Transactional
    public void publishScanCompleted(java.util.UUID tenantId, java.util.UUID fileId, ScanVerdict verdict, long scanDurationMs, String correlationId, String causationId) {
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(tenantId, fileId).orElseThrow();
        if (fileRecord.getStatus() == FileStatus.DELETED || fileRecord.getStatus() == FileStatus.ARCHIVED) {
            return;
        }
        fileEventPublisher.publishScanCompleted(
                fileRecord,
                verdict.malwareScanStatus(),
                verdict.reason(),
                scanDurationMs,
                malwareScanner.scannerName(),
                verdict.timedOut(),
                verdict.checksumBlacklisted(),
                correlationId,
                causationId
        );
    }

    /**
     * Marks a file as scan-failed so the retry scheduler can attempt scanning again later.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @param errorMessage Human-readable scanner failure message.
     * @return Performs a side effect by updating the file lifecycle state.
     */
    @Transactional
    public void markScanFailed(java.util.UUID tenantId, java.util.UUID fileId, String errorMessage) {
        FileRecord fileRecord = fileRecordRepository.findByTenantIdAndIdForUpdate(tenantId, fileId).orElseThrow();
        if (fileRecord.getStatus() == FileStatus.DELETED || fileRecord.getStatus() == FileStatus.ARCHIVED) {
            return;
        }
        if (fileRecord.getStatus() == FileStatus.PENDING_SCAN || fileRecord.getStatus() == FileStatus.SCAN_FAILED) {
            fileRecord.markScanFailed(errorMessage == null ? "The scanner failed without a message." : errorMessage);
            fileRecordRepository.save(fileRecord);
        }
    }
}

