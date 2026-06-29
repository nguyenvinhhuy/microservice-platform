package huynv.fileservice.event;

import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.repository.FileRecordRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Retries scan-failed files with distributed locking so malware scans remain resumable and multi-instance safe.
 */
@Component
public class ScanRetryScheduler {

    private final FileRecordRepository fileRecordRepository;
    private final FileServiceProperties properties;
    private final ScanResultHandler scanResultHandler;

    /**
     * Creates a retry scheduler for scan-failed files.
     *
     * @param fileRecordRepository Repository used to load retry-eligible files.
     * @param properties File-service properties containing retry spacing and limits.
     * @param scanResultHandler Scan handler used to re-run the scan workflow.
     * @return Initializes the scan retry scheduler.
     */
    public ScanRetryScheduler(FileRecordRepository fileRecordRepository, FileServiceProperties properties, ScanResultHandler scanResultHandler) {
        this.fileRecordRepository = Objects.requireNonNull(fileRecordRepository, "fileRecordRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.scanResultHandler = Objects.requireNonNull(scanResultHandler, "scanResultHandler");
    }

    /**
     * Retries scan-failed files whose retry delay window has elapsed and whose retry budget is not exhausted.
     *
     * @return Performs side effects by re-running malware scans for eligible files.
     */
    @Scheduled(fixedDelayString = "${file-service.scan.retry-delay:PT5M}")
    @SchedulerLock(name = "file-service-scan-retry-scheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional(readOnly = true)
    public void retryFailedScans() {
        Instant threshold = Instant.now().minus(properties.getScan().getRetryDelay());
        fileRecordRepository.findRetryEligible(FileStatus.SCAN_FAILED, threshold, PageRequest.of(0, 50))
                .stream()
                .filter(fileRecord -> fileRecord.getScanRetryCount() < properties.getScan().getMaxRetries())
                .forEach(scanResultHandler::retry);
    }
}

