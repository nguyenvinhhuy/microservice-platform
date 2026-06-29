package huynv.userservice.event;

import huynv.eventinfra.outbox.KafkaOutboxMessage;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.userservice.config.UserServiceProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes user-service outbox rows to Kafka with bounded retries and distributed scheduling.
 */
@Component
@ConditionalOnProperty(prefix = "user-service.outbox", name = "publisher-enabled", havingValue = "true", matchIfMissing = true)
public class UserOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserOutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaOutboxService kafkaOutboxService;
    private final UserServiceProperties properties;
    private final AtomicInteger inflightSends = new AtomicInteger(0);

    /**
     * Creates an outbox publisher for user-service Kafka events.
     *
     * @param kafkaTemplate Kafka template used for broker publishing.
     * @param kafkaOutboxService Shared outbox service used to claim and transition rows.
     * @param properties User-service outbox properties.
     * @param meterRegistry Meter registry used to expose outbox backlog metrics.
     * @return Initializes a user outbox publisher instance.
     */
    public UserOutboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaOutboxService kafkaOutboxService,
            UserServiceProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.kafkaOutboxService = Objects.requireNonNull(kafkaOutboxService, "kafkaOutboxService");
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        Gauge.builder("user_outbox_backlog_total", kafkaOutboxService::backlogSize)
                .description("Current number of user-service outbox rows waiting to be published.")
                .register(meterRegistry);
    }

    /**
     * Claims due outbox rows and publishes them to Kafka.
     *
     * @return Performs side effects by sending messages to Kafka and transitioning outbox state.
     */
    @Scheduled(fixedDelayString = "${user-service.outbox.fixed-delay:1s}")
    @SchedulerLock(name = "user-service-kafka-outbox-publisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT2S")
    public void publishBatch() {
        int available = Math.max(0, properties.getOutbox().getBatchSize() - inflightSends.get());
        if (available <= 0) {
            return;
        }
        List<KafkaOutboxMessage> messages = kafkaOutboxService.claimDueBatchWithLease(available, properties.getOutbox().getProcessingTimeout());
        for (KafkaOutboxMessage message : messages) {
            publishOne(message);
        }
    }

    /**
     * Publishes a single claimed outbox row and updates persisted state on success or failure.
     *
     * @param message Outbox message claimed for publishing.
     * @return Performs side effects by sending the message to Kafka and updating outbox state.
     */
    private void publishOne(KafkaOutboxMessage message) {
        inflightSends.incrementAndGet();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
            Map<String, String> headers = kafkaOutboxService.parseHeaders(message.getHeadersJson());
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() == null || header.getKey().isBlank() || header.getValue() == null) {
                    continue;
                }
                record.headers().add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
            }
            CompletableFuture<?> sendFuture = kafkaTemplate.send(record);
            sendFuture.whenComplete((ignored, throwable) -> completePublish(message, throwable));
        } catch (Exception ex) {
            completePublish(message, ex);
        }
    }

    /**
     * Completes outbox state transitions after a Kafka send attempt finishes.
     *
     * @param message Outbox message that was sent.
     * @param throwable Throwable returned by the send future, or null on success.
     * @return Performs side effects by transitioning the outbox row and updating inflight state.
     */
    private void completePublish(KafkaOutboxMessage message, Throwable throwable) {
        try {
            if (throwable == null) {
                kafkaOutboxService.markSent(message.getId());
                return;
            }
            kafkaOutboxService.markFailed(message.getId(), throwable.getMessage(), nextDelay(message.getRetryCount()));
            log.warn("User outbox publish failed id={} topic={} retryCount={} message={}", message.getId(), message.getTopic(), message.getRetryCount(), throwable.getMessage());
        } catch (Exception ex) {
            log.error("User outbox state transition failed id={} message={}", message.getId(), ex.getMessage(), ex);
        } finally {
            inflightSends.decrementAndGet();
        }
    }

    /**
     * Calculates the next exponential backoff delay for a failed publish attempt.
     *
     * @param retryCount Current persisted retry count.
     * @return Returns the next delay to apply before retrying the outbox row.
     */
    private Duration nextDelay(int retryCount) {
        double exponent = Math.max(0, retryCount);
        double calculatedMillis = properties.getOutbox().getInitialBackoff().toMillis() * Math.pow(properties.getOutbox().getBackoffMultiplier(), exponent);
        long boundedMillis = Math.min((long) calculatedMillis, properties.getOutbox().getMaxBackoff().toMillis());
        return Duration.ofMillis(Math.max(1, boundedMillis));
    }
}

