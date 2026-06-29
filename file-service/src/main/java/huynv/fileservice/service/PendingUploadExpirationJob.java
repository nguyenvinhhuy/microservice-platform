package huynv.fileservice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires stale pending uploads with distributed locking so abandoned reservations are reclaimed safely.
 */
@Component
public class PendingUploadExpirationJob {

    private final FileLifecycleService fileLifecycleService;

    /**
     * Creates a cleanup job that delegates stale pending-upload cleanup to the lifecycle service.
     *
     * @param fileLifecycleService Lifecycle service used to expire abandoned pending uploads.
     */
    public PendingUploadExpirationJob(FileLifecycleService fileLifecycleService) {
        this.fileLifecycleService = fileLifecycleService;
    }

    /**
     * Expires stale pending uploads on a fixed distributed schedule.
     *
     * @return Performs side effects by deleting abandoned pending uploads and releasing reserved quota.
     */
    @Scheduled(fixedDelayString = "${file-service.quota.pending-upload-ttl:PT1H}")
    @SchedulerLock(name = "file-service-pending-upload-cleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cleanupStalePendingUploads() {
        fileLifecycleService.cleanupStalePendingUploads();
    }
}

