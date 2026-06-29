package huynv.fileservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Centralizes counters, timers, and byte summaries used for file-service observability.
 */
@Component
public class FileMetrics {

    private final Counter uploadTotal;
    private final Counter downloadTotal;
    private final Counter uploadFailedTotal;
    private final Counter scanFailedTotal;
    private final Counter scanInfectedTotal;
    private final Counter scanTimeoutTotal;
    private final Counter presignedUrlTotal;
    private final Counter accessDeniedTotal;
    private final Counter rateLimitRejectedTotal;
    private final Timer uploadLatency;
    private final Timer minioRequestLatency;
    private final Timer scanDuration;
    private final DistributionSummary storageBytes;

    /**
     * Creates metric instruments used by file-service command and storage paths.
     *
     * @param meterRegistry Meter registry used to register counters, timers, and summaries.
     * @return Initializes the file metrics registry wrapper.
     */
    public FileMetrics(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.uploadTotal = Counter.builder("file_upload_total").register(meterRegistry);
        this.downloadTotal = Counter.builder("file_download_total").register(meterRegistry);
        this.uploadFailedTotal = Counter.builder("file_upload_failed_total").register(meterRegistry);
        this.scanFailedTotal = Counter.builder("file_scan_failed_total").register(meterRegistry);
        this.scanInfectedTotal = Counter.builder("file_scan_infected_total").register(meterRegistry);
        this.scanTimeoutTotal = Counter.builder("file_scan_timeout_total").register(meterRegistry);
        this.presignedUrlTotal = Counter.builder("file_presigned_url_total").register(meterRegistry);
        this.accessDeniedTotal = Counter.builder("file_access_denied_total").register(meterRegistry);
        this.rateLimitRejectedTotal = Counter.builder("file_rate_limit_rejected_total").register(meterRegistry);
        this.uploadLatency = Timer.builder("file_upload_latency_seconds").publishPercentileHistogram().register(meterRegistry);
        this.minioRequestLatency = Timer.builder("minio_request_latency_seconds").publishPercentileHistogram().register(meterRegistry);
        this.scanDuration = Timer.builder("file_scan_duration_seconds").publishPercentileHistogram().register(meterRegistry);
        this.storageBytes = DistributionSummary.builder("file_storage_bytes").register(meterRegistry);
    }

    /**
     * Records a successful file upload and its latency.
     *
     * @param bytes Uploaded byte size.
     * @param durationNanos Upload latency in nanoseconds.
     * @return Performs side effects by updating upload counters and timers.
     */
    public void recordUploadSuccess(long bytes, long durationNanos) {
        uploadTotal.increment();
        storageBytes.record(bytes);
        uploadLatency.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * Records a failed file upload attempt.
     *
     * @return Performs a side effect by incrementing the failed upload counter.
     */
    public void recordUploadFailure() {
        uploadFailedTotal.increment();
    }

    /**
     * Records a successful file download.
     *
     * @return Performs a side effect by incrementing the download counter.
     */
    public void recordDownload() {
        downloadTotal.increment();
    }

    /**
     * Records a generated pre-signed URL.
     *
     * @return Performs a side effect by incrementing the presigned URL counter.
     */
    public void recordPresignedUrl() {
        presignedUrlTotal.increment();
    }

    /**
     * Records a denied access attempt.
     *
     * @return Performs a side effect by incrementing the denied access counter.
     */
    public void recordAccessDenied() {
        accessDeniedTotal.increment();
    }

    /**
     * Records a failed scan transition.
     *
     * @return Performs a side effect by incrementing the scan failure counter.
     */
    public void recordScanFailure() {
        scanFailedTotal.increment();
    }

    /**
     * Records a completed malware scan duration.
     *
     * @param durationNanos Scan duration in nanoseconds.
     * @return Performs a side effect by updating the scan duration timer.
     */
    public void recordScanDuration(long durationNanos) {
        scanDuration.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * Records an infected-file detection.
     *
     * @return Performs a side effect by incrementing the infected-file counter.
     */
    public void recordScanInfected() {
        scanInfectedTotal.increment();
    }

    /**
     * Records a scan timeout.
     *
     * @return Performs a side effect by incrementing the timeout counter.
     */
    public void recordScanTimeout() {
        scanTimeoutTotal.increment();
    }

    /**
     * Records a distributed rate-limit rejection.
     *
     * @param bucket Rate-limit bucket name for diagnostics.
     * @return Performs a side effect by incrementing the rate-limit rejection counter.
     */
    public void recordRateLimitRejection(String bucket) {
        rateLimitRejectedTotal.increment();
    }

    /**
     * Records the latency of a storage request made against MinIO.
     *
     * @param durationNanos Storage request latency in nanoseconds.
     * @return Performs a side effect by updating the storage latency timer.
     */
    public void recordMinioLatency(long durationNanos) {
        minioRequestLatency.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}

