package huynv.eventinfra.retry;

import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.eventinfra.util.MdcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Consumes retry tier topics and forwards messages back to their retry target topic when due.
 */
@Component
public class RetryDelayConsumer {

    private static final Logger log = LoggerFactory.getLogger(RetryDelayConsumer.class);

    private final NotificationProperties properties;
    private final KafkaOutboxService outboxService;

    /**
     * Creates a retry delay consumer for forwarding tiered retry messages back to their processing topic.
     *
     * @param properties Notification properties containing retry topic names.
     * @param outboxService Outbox service used to persist delayed forwarding into the retry target topic.
     * @return Initializes a retry delay consumer.
     */
    public RetryDelayConsumer(NotificationProperties properties, KafkaOutboxService outboxService) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
    }

    /**
     * Consumes messages from the 1 minute retry topic and forwards them when their due time is reached.
     *
     * @param record Retry record.
     * @param acknowledgment Manual acknowledgment handle.
     * @return Performs side effects by forwarding ready retry records to their target topic.
     */
    @KafkaListener(
            id = "notification-retry-1m",
            topics = "${notification.kafka.retry-1m-topic:notification.retry.1m}",
            groupId = "${notification.kafka.group-id:notification-service}-retry-1m",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRetry1m(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forwardWhenDue(record, acknowledgment);
    }

    /**
     * Consumes messages from the 5 minute retry topic and forwards them when their due time is reached.
     *
     * @param record Retry record.
     * @param acknowledgment Manual acknowledgment handle.
     * @return Performs side effects by forwarding ready retry records to their target topic.
     */
    @KafkaListener(
            id = "notification-retry-5m",
            topics = "${notification.kafka.retry-5m-topic:notification.retry.5m}",
            groupId = "${notification.kafka.group-id:notification-service}-retry-5m",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRetry5m(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forwardWhenDue(record, acknowledgment);
    }

    /**
     * Consumes messages from the 30 minute retry topic and forwards them when their due time is reached.
     *
     * @param record Retry record.
     * @param acknowledgment Manual acknowledgment handle.
     * @return Performs side effects by forwarding ready retry records to their target topic.
     */
    @KafkaListener(
            id = "notification-retry-30m",
            topics = "${notification.kafka.retry-30m-topic:notification.retry.30m}",
            groupId = "${notification.kafka.group-id:notification-service}-retry-30m",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRetry30m(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forwardWhenDue(record, acknowledgment);
    }

    /**
     * Forwards a retry record to its target topic when its due time is reached.
     *
     * @param record Retry record consumed from a retry tier topic.
     * @param acknowledgment Manual acknowledgment handle used to delay or commit offset.
     * @return Performs side effects by forwarding the record to the target topic or delaying consumption.
     */
    private void forwardWhenDue(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(acknowledgment, "acknowledgment");

            Long dueAt = RetryHeaders.readLong(record.headers(), RetryHeaders.RETRY_DUE_AT_MS);
            OffsetDateTime dueTime = dueAt == null
                    ? OffsetDateTime.now()
                    : OffsetDateTime.ofInstant(Instant.ofEpochMilli(dueAt), ZoneOffset.UTC);

            String target = RetryHeaders.readString(record.headers(), RetryHeaders.RETRY_TARGET_TOPIC);
            if (target == null || target.isBlank()) {
                target = properties.getKafka().getEventsTopic();
            }

            Map<String, String> mdc = new HashMap<>();
            mdc.put("retry_source_topic", record.topic());
            mdc.put("retry_target_topic", target);
            mdc.put("retry_partition", String.valueOf(record.partition()));
            mdc.put("retry_offset", String.valueOf(record.offset()));
            MdcUtil.putAll(mdc);

            Map<String, String> headers = new HashMap<>();
            record.headers().forEach(h -> headers.put(h.key(), h.value() == null ? null : new String(h.value(), java.nio.charset.StandardCharsets.UTF_8)));
            outboxService.enqueue(target, record.key(), record.value(), headers, KafkaOutboxPurpose.RETRY, dueTime);
            acknowledgment.acknowledge();
        } finally {
            MdcUtil.clear();
        }
    }
}

