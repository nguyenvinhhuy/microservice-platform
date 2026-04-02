package huynv.eventinfra.outbox;

import huynv.eventinfra.config.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes committed outbox rows to Kafka with at-least-once delivery and bounded retry behavior.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaOutboxService outboxService;
    private final NotificationProperties properties;
    private final KafkaOutboxPublishBackoffPolicy backoffPolicy;
    private final Timer publishLatency;
    private final Counter publishSuccess;
    private final Counter publishFailure;
    private final AtomicInteger inflightSends = new AtomicInteger(0);

    @Value("${notification.outbox.publisher.enabled:true}")
    private boolean publisherEnabled;

    /**
     * Creates an outbox publisher that exposes backlog gauges and publish metrics.
     *
     * @param kafkaTemplate Kafka template used to publish payloads to Kafka.
     * @param outboxService Outbox service used to claim and update outbox rows.
     * @param properties Notification properties controlling publishing batch size and timeouts.
     * @param meterRegistry Meter registry used to register publisher counters, timers, and gauges.
     * @return Initializes a Kafka outbox publisher instance.
     */
    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                KafkaOutboxService outboxService,
                                NotificationProperties properties,
                                MeterRegistry meterRegistry) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.backoffPolicy = new KafkaOutboxPublishBackoffPolicy(properties, 0.2);
        this.publishLatency = Timer.builder("notification_outbox_publish_latency_seconds")
                .description("Latency of Kafka outbox publishing loop in seconds.")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.publishSuccess = Counter.builder("notification_outbox_published_total")
                .description("Total number of outbox messages successfully published to Kafka.")
                .register(meterRegistry);
        this.publishFailure = Counter.builder("notification_outbox_publish_failed_total")
                .description("Total number of outbox messages that failed publishing to Kafka.")
                .register(meterRegistry);

        Gauge.builder("dispatcher_queue_depth", () -> outboxService.backlogSize(KafkaOutboxPurpose.DISPATCH))
                .description("Current outbox backlog size for notification dispatch publishing.")
                .register(meterRegistry);
        Gauge.builder("notification_retry_queue_depth", () -> outboxService.backlogSize(KafkaOutboxPurpose.RETRY))
                .description("Current outbox backlog size for notification retry publishing.")
                .register(meterRegistry);
        Gauge.builder("notification_dlq_queue_depth", () -> outboxService.backlogSize(KafkaOutboxPurpose.DLQ))
                .description("Current outbox backlog size for notification DLQ publishing.")
                .register(meterRegistry);
        Gauge.builder("notification_dlq_replay_queue_depth", () -> outboxService.backlogSize(KafkaOutboxPurpose.DLQ_REPLAY))
                .description("Current outbox backlog size for notification DLQ replay publishing.")
                .register(meterRegistry);
        Gauge.builder("notification_internal_queue_depth", () -> outboxService.backlogSize(KafkaOutboxPurpose.INTERNAL))
                .description("Current outbox backlog size for internal topic republishing.")
                .register(meterRegistry);
    }

    /**
     * Publishes due outbox rows to Kafka and updates persisted state for retry and auditing.
     *
     * @return Performs side effects by sending messages to Kafka and transitioning outbox state.
     */
    @Scheduled(fixedDelayString = "${notification.outbox.publisher.fixed-delay-ms:250}")
    @SchedulerLock(name = "notification-service-kafka-outbox-publisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT2S")
    public void publishBatch() {
        if (!publisherEnabled) {
            log.debug("Kafka outbox publisher disabled.");
            return;
        }
        int maxInflight = properties.getOutbox().getPublisher().getMaxInflight();
        int inflight = inflightSends.get();
        int available = Math.max(0, maxInflight - inflight);
        if (available <= 0) {
            log.warn("Kafka outbox publish skipped due to inflight backpressure inflight={} maxInflight={}", inflight, maxInflight);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            int batchSize = properties.getOutbox().getPublisher().getBatchSize();
            long processingTimeoutMs = properties.getOutbox().getPublisher().getProcessingTimeoutMs();
            int claimSize = Math.min(batchSize, available);
            List<KafkaOutboxMessage> messages = outboxService.claimDueBatchWithLease(claimSize, Duration.ofMillis(processingTimeoutMs));
            for (KafkaOutboxMessage message : messages) {
                publishOne(message);
            }
        } catch (Exception ex) {
            log.error("Kafka outbox publisher iteration failed.", ex);
        } finally {
            publishLatency.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Publishes a single claimed outbox message and updates its persisted state based on the broker acknowledgment.
     *
     * @param message Outbox message claimed for publishing.
     * @return Performs side effects by sending to Kafka and transitioning the outbox row to SENT or FAILED.
     */
    private void publishOne(KafkaOutboxMessage message) {
        UUID id = message.getId();
        inflightSends.incrementAndGet();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
            Map<String, String> headers = outboxService.parseHeaders(message.getHeadersJson());
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() == null || header.getKey().isBlank()) {
                    continue;
                }
                if (header.getValue() == null) {
                    continue;
                }
                record.headers().remove(header.getKey());
                record.headers().add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
            }

            CompletableFuture<?> future = kafkaTemplate.send(record);
            future.whenComplete((ignored, throwable) -> {
                try {
                    if (throwable == null) {
                        outboxService.markSent(id);
                        publishSuccess.increment();
                        return;
                    }
                    Duration delay = backoffPolicy.nextDelay(message.getRetryCount());
                    outboxService.markFailed(id, throwable.getMessage(), delay);
                    publishFailure.increment();
                    log.warn("Kafka outbox publish failed id={} topic={} purpose={} retryCount={} nextDelayMs={} message={}",
                            id,
                            message.getTopic(),
                            message.getPurpose(),
                            message.getRetryCount(),
                            delay.toMillis(),
                            throwable.getMessage());
                } catch (Exception ex) {
                    log.error("Kafka outbox state transition failed id={} topic={} message={}", id, message.getTopic(), ex.getMessage(), ex);
                } finally {
                    inflightSends.decrementAndGet();
                }
            });
        } catch (Exception ex) {
            Duration delay = backoffPolicy.nextDelay(message.getRetryCount());
            outboxService.markFailed(id, ex.getMessage(), delay);
            publishFailure.increment();
            log.warn("Kafka outbox publish failed id={} topic={} purpose={} retryCount={} nextDelayMs={} message={}",
                    id,
                    message.getTopic(),
                    message.getPurpose(),
                    message.getRetryCount(),
                    delay.toMillis(),
                    ex.getMessage());
            inflightSends.decrementAndGet();
        }
    }
}

