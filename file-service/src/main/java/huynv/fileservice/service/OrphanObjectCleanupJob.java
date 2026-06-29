package huynv.fileservice.service;

import huynv.fileservice.cache.FileCacheService;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.repository.FileRecordRepository;
import huynv.fileservice.storage.MinioStorageService;
import java.util.EnumSet;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes orphaned objects whose metadata already indicates the file is deleted or upload-expired.
 */
@Component
public class OrphanObjectCleanupJob {

    private final FileRecordRepository fileRecordRepository;
    private final MinioStorageService minioStorageService;
    private final FileCacheService fileCacheService;

    /**
     * Creates an orphan-object cleanup job backed by metadata rows and the MinIO storage adapter.
     *
     * @param fileRecordRepository Repository used to load cleanup candidates.
     * @param minioStorageService Storage service used to remove orphaned objects.
     * @param fileCacheService Cache service used to evict stale metadata entries.
     */
    public OrphanObjectCleanupJob(FileRecordRepository fileRecordRepository, MinioStorageService minioStorageService, FileCacheService fileCacheService) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.minioStorageService = Objects.requireNonNull(minioStorageService, "minioStorageService");
        this.fileCacheService = Objects.requireNonNull(fileCacheService, "fileCacheService");
    }

    /**
     * Removes orphaned objects for deleted and upload-expired file records in bounded batches.
     *
     * @return Performs side effects by deleting orphaned storage objects and evicting stale cache entries.
     */
    @Scheduled(fixedDelayString = "PT20M")
    @SchedulerLock(name = "file-service-orphan-object-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional(readOnly = true)
    public void cleanupOrphanObjects() {
        fileRecordRepository.findByStatusInOrderByUpdatedAtAsc(EnumSet.of(FileStatus.DELETED, FileStatus.UPLOAD_EXPIRED), PageRequest.of(0, 100))
                .stream()
                .forEach(fileRecord -> {
                    minioStorageService.deleteObject(fileRecord.getBucket(), fileRecord.getObjectKey());
                    fileCacheService.evictMetadata(fileRecord.getTenantId(), fileRecord.getId());
                });
    }
}

