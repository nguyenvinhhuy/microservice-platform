package huynv.fileservice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cleans up expired multipart upload sessions with distributed locking so stale uploads do not accumulate.
 */
@Component
public class MultipartCleanupJob {

    private final MultipartUploadService multipartUploadService;

    /**
     * Creates a cleanup job that delegates expired-session cleanup to the multipart upload service.
     *
     * @param multipartUploadService Multipart upload service used to abort and expire stale sessions.
     */
    public MultipartCleanupJob(MultipartUploadService multipartUploadService) {
        this.multipartUploadService = multipartUploadService;
    }

    /**
     * Cleans up expired multipart upload sessions on a fixed distributed schedule.
     *
     * @return Performs side effects by aborting expired multipart uploads and releasing reserved resources.
     */
    @Scheduled(fixedDelayString = "${file-service.multipart.cleanup-fixed-delay:PT30M}")
    @SchedulerLock(name = "file-service-multipart-cleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT30S")
    public void cleanupExpiredMultipartUploads() {
        multipartUploadService.cleanupExpiredSessions();
    }
}

