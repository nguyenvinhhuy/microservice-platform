package huynv.fileservice.service;

import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.exception.StorageOperationException;
import huynv.fileservice.repository.FileRecordRepository;
import huynv.fileservice.storage.MinioStorageService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Reconciles file metadata against object storage so missing objects are detected and marked for recovery or cleanup.
 */
@Component
public class StorageConsistencyJob {

    private final FileRecordRepository fileRecordRepository;
    private final MinioStorageService minioStorageService;

    /**
     * Creates a storage consistency job backed by metadata and object-storage lookups.
     *
     * @param fileRecordRepository Repository used to load candidate file records.
     * @param minioStorageService Storage service used to verify object existence.
     * @return Initializes the storage consistency job.
     */
    public StorageConsistencyJob(FileRecordRepository fileRecordRepository, MinioStorageService minioStorageService) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.minioStorageService = Objects.requireNonNull(minioStorageService, "minioStorageService");
    }

    /**
     * Checks a bounded batch of active file records for storage drift and marks missing objects for recovery.
     *
     * @return Performs side effects by transitioning missing-object records into recovery-oriented lifecycle states.
     */
    @Scheduled(fixedDelayString = "PT10M")
    @SchedulerLock(name = "file-service-storage-consistency-job", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void reconcile() {
        fileRecordRepository.findByStatusInOrderByUpdatedAtAsc(
                        EnumSet.of(FileStatus.PENDING_UPLOAD, FileStatus.PENDING_SCAN, FileStatus.AVAILABLE, FileStatus.SCAN_FAILED),
                        PageRequest.of(0, 100)
                )
                .stream()
                .forEach(this::reconcileRecord);
    }

    /**
     * Verifies a single record against storage and marks missing objects for follow-up processing.
     *
     * @param fileRecord File metadata record to reconcile.
     * @return Performs side effects by updating the file lifecycle state when storage drift is detected.
     */
    public void reconcileRecord(FileRecord fileRecord) {
        try {
            minioStorageService.statObject(fileRecord.getBucket(), fileRecord.getObjectKey());
        } catch (StorageOperationException ex) {
            if ("STORAGE_OBJECT_NOT_FOUND".equals(ex.getErrorCode())) {
                if (fileRecord.getStatus() == FileStatus.PENDING_UPLOAD) {
                    fileRecord.markUploadExpired();
                } else if (fileRecord.getStatus() == FileStatus.PENDING_SCAN || fileRecord.getStatus() == FileStatus.AVAILABLE || fileRecord.getStatus() == FileStatus.SCAN_FAILED) {
                    fileRecord.markDeletePending();
                }
                fileRecordRepository.save(fileRecord);
                return;
            }
            throw ex;
        }
    }
}

