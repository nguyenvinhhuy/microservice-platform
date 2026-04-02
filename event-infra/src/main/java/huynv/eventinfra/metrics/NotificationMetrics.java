package huynv.eventinfra.metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Captures notification processing and delivery metrics for Prometheus scraping.
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter sentTotal;
    private final Counter failedTotal;
    private final Counter skippedTotal;
    private final Counter retryRate;
    private final Counter recipientResolutionFailures;
    private final Timer processingLatency;
    private final ConcurrentHashMap<String, Counter> retryCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rateLimitedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> channelLatencyTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> sentCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> failedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> retryTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> dlqTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> dlqReplayTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> dlqReplayDroppedTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> dispatchLatencyTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> providerTimeoutTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rateLimitRejectedTotals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> workerQueueRejectedTotals = new ConcurrentHashMap<>();

    /**
     * Creates metric instruments for notification processing and delivery outcomes.
     *
     * @param meterRegistry Meter registry used to register counters and timers.
     * @return Initializes the notification metrics reporter.
     */
    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.sentTotal = Counter.builder("notifications_sent_total")
                .description("Total number of notifications successfully delivered.")
                .register(this.meterRegistry);
        this.failedTotal = Counter.builder("notifications_failed_total")
                .description("Total number of notifications that failed delivery.")
                .register(this.meterRegistry);
        this.skippedTotal = Counter.builder("notifications_skipped_total")
                .description("Total number of notifications skipped due to missing contact data or disabled channels.")
                .register(this.meterRegistry);
        this.retryRate = Counter.builder("notification_retry_rate")
                .description("Total number of notification retries scheduled (use PromQL rate() to compute retry rate).")
                .register(this.meterRegistry);
        this.recipientResolutionFailures = Counter.builder("notification_recipient_resolution_failures_total")
                .description("Total number of notification recipient resolution failures.")
                .register(this.meterRegistry);
        this.processingLatency = Timer.builder("notification_processing_latency_seconds")
                .description("Notification processing latency in seconds.")
                .publishPercentileHistogram()
                .register(this.meterRegistry);
    }

    /**
     * Increments the total successful notification counter.
     *
     * @return Performs a side effect by incrementing the sent counter.
     */
    public void markSent() {
        sentTotal.increment();
    }

    /**
     * Increments the successful notification counter with tenant/channel/provider/priority labels.
     *
     * @param channel Channel used for delivery.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notifications_sent_total with required labels.
     */
    public void markSent(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = sentCounters.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notifications_sent_total")
                        .description("Total number of notifications successfully delivered.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the total failed notification counter.
     *
     * @return Performs a side effect by incrementing the failed counter.
     */
    public void markFailed() {
        failedTotal.increment();
    }

    /**
     * Increments the failed notification counter with tenant/channel/provider/priority labels.
     *
     * @param channel Channel used for delivery.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notifications_failed_total with required labels.
     */
    public void markFailed(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = failedCounters.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notifications_failed_total")
                        .description("Total number of notifications that failed delivery.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the total skipped notification counter.
     *
     * @return Performs a side effect by incrementing the skipped counter.
     */
    public void markSkipped() {
        skippedTotal.increment();
    }

    /**
     * Records processing latency using the configured latency timer.
     *
     * @param startNanos Start timestamp in nanoseconds.
     * @return Performs a side effect by recording processing latency in the timer.
     */
    public void recordProcessingLatency(long startNanos) {
        long duration = System.nanoTime() - startNanos;
        processingLatency.record(duration, TimeUnit.NANOSECONDS);
    }

    /**
     * Increments the retry scheduling rate counter used for retry storm detection.
     *
     * @return Performs a side effect by incrementing notification_retry_rate.
     */
    public void incrementRetryRate() {
        retryRate.increment();
    }

    /**
     * Increments the retry counter with tenant/channel/provider/priority labels for provider-facing retry analysis.
     *
     * @param channel Channel that will be retried.
     * @param provider Provider name involved in the retry decision.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notification_retry_total with required labels.
     */
    public void incrementRetryTotal(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = retryTotals.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notification_retry_total")
                        .description("Total number of notification retries requested due to transient dependency or rate limiting issues.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the DLQ counter with tenant/channel/provider/priority labels when available.
     *
     * @param channel Channel associated with the record when known.
     * @param provider Provider name associated with the record when known.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notification_dlq_total with required labels.
     */
    public void incrementDlqTotal(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = dlqTotals.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notification_dlq_total")
                        .description("Total number of notification records published to a dead-letter topic.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments a DLQ replay counter to track operator replay activity by original topic.
     *
     * @param originalTopic Original topic that the DLQ record will be republished to.
     * @return Performs a side effect by incrementing notification_dlq_replay_total.
     */
    public void incrementDlqReplay(String originalTopic) {
        Counter counter = dlqReplayTotals.computeIfAbsent(safeTagValue(originalTopic), ignored ->
                Counter.builder("notification_dlq_replay_total")
                        .description("Total number of DLQ records replayed back to their original topic.")
                        .tag("original_topic", safeTagValue(originalTopic))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments a DLQ replay dropped counter when a record is discarded due to exceeding replay attempts.
     *
     * @param originalTopic Original topic that the DLQ record targeted for replay.
     * @param dlqTopic DLQ topic that contained the record being replayed.
     * @return Performs a side effect by incrementing notification_dlq_replay_dropped_total.
     */
    public void incrementDlqReplayDroppedTotal(String originalTopic, String dlqTopic) {
        String key = safeTagValue(originalTopic) + "|" + safeTagValue(dlqTopic);
        Counter counter = dlqReplayDroppedTotals.computeIfAbsent(key, ignored ->
                Counter.builder("notification_dlq_replay_dropped_total")
                        .description("Total number of DLQ replay records dropped due to exceeding max replay attempts.")
                        .tag("original_topic", safeTagValue(originalTopic))
                        .tag("dlq_topic", safeTagValue(dlqTopic))
                        .tag("tenantId", "unknown")
                        .tag("channel", "unknown")
                        .tag("provider", "unknown")
                        .tag("priority", "unknown")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the provider timeout counter used for alerting on external provider latency regressions.
     *
     * @param channel Delivery channel used for the provider call.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notification_provider_timeout_total.
     */
    public void incrementProviderTimeoutTotal(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = providerTimeoutTotals.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notification_provider_timeout_total")
                        .description("Total number of notification provider calls that timed out.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the rate limit rejection counter used for tracking throttled provider sends.
     *
     * @param channel Delivery channel being rate limited.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notification_rate_limit_rejected_total.
     */
    public void incrementRateLimitRejectedTotal(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = rateLimitRejectedTotals.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notification_rate_limit_rejected_total")
                        .description("Total number of notification sends rejected due to rate limiting.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the worker queue rejection counter used for backpressure monitoring.
     *
     * @param channel Delivery channel whose worker pool rejected the task.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @return Performs a side effect by incrementing notification_worker_queue_rejected_total.
     */
    public void incrementWorkerQueueRejectedTotal(Object channel, String provider, Long tenantId, String priority) {
        Counter counter = workerQueueRejectedTotals.computeIfAbsent(dimKey(channel, provider, tenantId, priority), ignored ->
                Counter.builder("notification_worker_queue_rejected_total")
                        .description("Total number of notification jobs rejected due to worker queue capacity limits.")
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the recipient resolution failure counter used for alerting on upstream contract or dependency issues.
     *
     * @return Performs a side effect by incrementing notification_recipient_resolution_failures_total.
     */
    public void incrementRecipientResolutionFailure() {
        recipientResolutionFailures.increment();
    }

    /**
     * Increments the retry counter for a record that will be re-processed by the consumer.
     *
     * @param originalTopic Topic that the record was consumed from.
     * @param consumerGroup Consumer group that scheduled the retry.
     * @return Performs a side effect by incrementing notification_retry_total.
     */
    public void incrementRetry(String originalTopic, String consumerGroup) {
        Counter counter = retryCounters.computeIfAbsent(metricKey(originalTopic, consumerGroup), ignored ->
                Counter.builder("notification_retry_total")
                        .description("Total number of notification processing retries scheduled by Kafka error handlers.")
                        .tag("original_topic", safeTagValue(originalTopic))
                        .tag("consumer_group", safeTagValue(consumerGroup))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the DLQ counter when a record is published to a dead-letter topic.
     *
     * @param originalTopic Topic that the record originated from.
     * @param consumerGroup Consumer group that published the record to the DLQ.
     * @return Performs a side effect by incrementing notification_dlq_total.
     */
    public void incrementDlq(String originalTopic, String consumerGroup) {
        Counter counter = dlqCounters.computeIfAbsent(metricKey(originalTopic, consumerGroup), ignored ->
                Counter.builder("notification_dlq_total")
                        .description("Total number of notification records published to a dead-letter topic.")
                        .tag("original_topic", safeTagValue(originalTopic))
                        .tag("consumer_group", safeTagValue(consumerGroup))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Increments the per-channel rate limiting counter when a worker is throttled.
     *
     * @param channel Channel that was rate limited.
     * @return Performs a side effect by incrementing notification_rate_limited_total.
     */
    public void incrementRateLimited(Object channel) {
        Counter counter = rateLimitedCounters.computeIfAbsent(channelName(channel), ignored ->
                Counter.builder("notification_rate_limited_total")
                        .description("Total number of notification sends that were delayed due to provider rate limiting.")
                        .tag("channel", channelName(channel))
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Records per-channel delivery latency for provider integrations.
     *
     * @param channel Channel used for delivery.
     * @param provider Provider name used for delivery.
     * @param deliveryStatus Delivery status label such as SENT or FAILED.
     * @param startNanos Start timestamp in nanoseconds for the measured operation.
     * @return Performs a side effect by recording latency into notification_channel_latency_seconds.
     */
    public void recordChannelLatency(Object channel, String provider, String deliveryStatus, long startNanos) {
        long duration = System.nanoTime() - startNanos;
        Timer timer = channelLatencyTimers.computeIfAbsent(channelLatencyKey(channel, provider, deliveryStatus), ignored ->
                Timer.builder("notification_channel_latency_seconds")
                        .description("Latency of channel provider delivery calls in seconds.")
                        .publishPercentileHistogram()
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("delivery_status", safeTagValue(deliveryStatus))
                        .register(meterRegistry)
        );
        timer.record(duration, TimeUnit.NANOSECONDS);
    }

    /**
     * Records per-channel delivery latency with tenant and priority labels for multi-tenant SLO tracking.
     *
     * @param channel Channel used for delivery.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @param deliveryStatus Delivery status label such as SENT or FAILED.
     * @param startNanos Start timestamp in nanoseconds for the measured operation.
     * @return Performs a side effect by recording latency into notification_channel_latency_seconds with required labels.
     */
    public void recordChannelLatency(Object channel,
                                     String provider,
                                     Long tenantId,
                                     String priority,
                                     String deliveryStatus,
                                     long startNanos) {
        long duration = System.nanoTime() - startNanos;
        String key = channelName(channel) + "|" + safeTagValue(provider) + "|" + safeTagValue(tenantId == null ? null : String.valueOf(tenantId)) + "|" + safeTagValue(priority) + "|" + safeTagValue(deliveryStatus);
        Timer timer = channelLatencyTimers.computeIfAbsent(key, ignored ->
                Timer.builder("notification_channel_latency_seconds")
                        .description("Latency of channel provider delivery calls in seconds.")
                        .publishPercentileHistogram()
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .tag("delivery_status", safeTagValue(deliveryStatus))
                        .register(meterRegistry)
        );
        timer.record(duration, TimeUnit.NANOSECONDS);
    }

    /**
     * Records end-to-end dispatch latency for a notification job execution.
     *
     * @param channel Channel used for delivery.
     * @param provider Provider name used for delivery.
     * @param tenantId Tenant identifier for multi-tenant observability.
     * @param priority Priority label for dispatch observability.
     * @param startNanos Start timestamp in nanoseconds for the measured operation.
     * @return Performs a side effect by recording latency into notification_dispatch_latency_seconds.
     */
    public void recordDispatchLatency(Object channel, String provider, Long tenantId, String priority, long startNanos) {
        long duration = System.nanoTime() - startNanos;
        String key = dimKey(channel, provider, tenantId, priority);
        Timer timer = dispatchLatencyTimers.computeIfAbsent(key, ignored ->
                Timer.builder("notification_dispatch_latency_seconds")
                        .description("End-to-end dispatch latency for notification job execution in seconds.")
                        .publishPercentileHistogram()
                        .tag("channel", channelName(channel))
                        .tag("provider", safeTagValue(provider))
                        .tag("tenantId", safeTagValue(tenantId == null ? null : String.valueOf(tenantId)))
                        .tag("priority", safeTagValue(priority))
                        .register(meterRegistry)
        );
        timer.record(duration, TimeUnit.NANOSECONDS);
    }

    /**
     * Builds a stable meter key for topic-scoped counters.
     *
     * @param originalTopic Topic that the record originated from.
     * @param consumerGroup Consumer group that processed the record.
     * @return Returns a stable key used to memoize meters.
     */
    private static String metricKey(String originalTopic, String consumerGroup) {
        return safeTagValue(originalTopic) + "|" + safeTagValue(consumerGroup);
    }

    /**
     * Builds a stable timer key for per-channel latency measurements.
     *
     * @param channel Delivery channel used for sending.
     * @param provider Provider name used for sending.
     * @param deliveryStatus Delivery status label.
     * @return Returns a stable key used to memoize timers.
     */
    private static String channelLatencyKey(Object channel, String provider, String deliveryStatus) {
        return channelName(channel) + "|" + safeTagValue(provider) + "|" + safeTagValue(deliveryStatus);
    }

    /**
     * Builds a stable key used to memoize meter instances for tenant/channel/provider/priority dimensions.
     *
     * @param channel Delivery channel used for sending.
     * @param provider Provider name used for sending.
     * @param tenantId Tenant identifier used for multi-tenant observability.
     * @param priority Priority label used for dispatch observability.
     * @return Returns a stable key used to memoize meters and timers.
     */
    private static String dimKey(Object channel, String provider, Long tenantId, String priority) {
        return channelName(channel)
                + "|" + safeTagValue(provider)
                + "|" + safeTagValue(tenantId == null ? null : String.valueOf(tenantId))
                + "|" + safeTagValue(priority);
    }

    /**
     * Converts a channel enum or string into a stable metric label value.
     *
     * @param channel Channel label source.
     * @return Returns the normalized channel label.
     */
    private static String channelName(Object channel) {
        if (channel == null) {
            return "unknown";
        }
        return safeTagValue(String.valueOf(channel));
    }

    /**
     * Normalizes a tag value to a short, non-blank string suitable for meter tags.
     *
     * @param value Tag value candidate.
     * @return Returns a safe tag value.
     */
    private static String safeTagValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 250) {
            return trimmed;
        }
        return new String(bytes, 0, 250, StandardCharsets.UTF_8);
    }
}



