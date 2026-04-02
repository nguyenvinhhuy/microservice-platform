package huynv.eventinfra.dlq;

import huynv.event.idempotency.IdempotencyService;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Replays dead-lettered Kafka records by republishing them to their original topic through the transactional outbox.
 */
@Component
@ConditionalOnExpression("${notification.dlq-replay.enabled:false} || ${notification.dlq.replay.enabled:false}")
public class DlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class);
    private static final String REPLAY_COUNT_HEADER = "x-replay-count";

    private final NotificationProperties properties;
    private final KafkaOutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final NotificationMetrics metrics;

    /**
     * Creates a DLQ replay service that republishes records to their original topic with idempotency checks.
     *
     * @param properties Notification properties containing DLQ topic name.
     * @param outboxService Outbox service used to persist replay publishes for asynchronous Kafka delivery.
     * @param idempotencyService Idempotency service used to avoid replaying the same DLQ record more than once.
     * @param metrics Metrics used to track DLQ replay outcomes.
     * @return Initializes a DLQ replay service.
     */
    public DlqReplayService(NotificationProperties properties,
                            KafkaOutboxService outboxService,
                            IdempotencyService idempotencyService,
                            NotificationMetrics metrics) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Consumes DLQ records and republishes them to the original topic when replay is enabled.
     *
     * @param record Kafka record consumed from the DLQ topic.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by republishing DLQ records into their original topic.
     */
    @KafkaListener(
            id = "notification-dlq-replay",
            topics = "${notification.kafka.dlq-topic:notification.dlq}",
            groupId = "${notification.dlq-replay.group-id:notification-dlq-replay}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDlqRecord(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(acknowledgment, "acknowledgment");
        replayOnce(record);
        acknowledgment.acknowledge();
    }

    /**
     * Replays a DLQ record once by republishing it to the original topic through the outbox.
     *
     * @param record DLQ record to replay.
     * @return Performs side effects by inserting an outbox row for replay and marking the DLQ record as processed.
     */
    @Transactional
    public void replayOnce(ConsumerRecord<String, String> record) {
        Objects.requireNonNull(record, "record");

        String idempotencyKey = "dlq-replay|" + record.topic() + "|" + record.partition() + "|" + record.offset();
        if (idempotencyService.alreadyProcessed(idempotencyKey)) {
            return;
        }

        int maxReplayAttempts = properties.getDlq().getMaxReplayAttempts();
        int currentReplayCount = replayCount(record, maxReplayAttempts);
        int nextReplayCount = currentReplayCount + 1;
        if (nextReplayCount > maxReplayAttempts) {
            idempotencyService.markProcessed(idempotencyKey);
            metrics.incrementDlqReplayDroppedTotal(originalTopic(record), record.topic());
            log.error("CRITICAL: DLQ replay dropped due to max attempts exceeded dlqTopic={} originalTopic={} partition={} offset={} replayCount={} maxReplayAttempts={}",
                    record.topic(),
                    originalTopic(record),
                    record.partition(),
                    record.offset(),
                    currentReplayCount,
                    maxReplayAttempts);
            return;
        }

        String originalTopic = headerValue(record, "original_topic");
        if (originalTopic == null || originalTopic.isBlank()) {
            originalTopic = headerValue(record, "originalTopic");
        }
        if (originalTopic == null || originalTopic.isBlank()) {
            originalTopic = properties.getKafka().getEventsTopic();
        }

        Map<String, String> headers = toHeaderMap(record);
        headers.put("original_topic", originalTopic);
        headers.put("dlq_replay_id", idempotencyKey);
        headers.put("dlq_replayed_at", OffsetDateTime.now().toString());
        headers.put(REPLAY_COUNT_HEADER, String.valueOf(nextReplayCount));

        outboxService.enqueue(originalTopic, record.key(), record.value(), headers, KafkaOutboxPurpose.DLQ_REPLAY, OffsetDateTime.now());
        idempotencyService.markProcessed(idempotencyKey);
        metrics.incrementDlqReplay(originalTopic);

        log.info("DLQ record replayed originalTopic={} dlqTopic={} partition={} offset={}",
                originalTopic,
                record.topic(),
                record.partition(),
                record.offset());
    }

    /**
     * Resolves the current replay count from headers and applies a defensive fallback on invalid values.
     *
     * @param record DLQ record containing replay headers.
     * @param maxReplayAttempts Maximum allowed replay attempts used for defensive fallback.
     * @return Returns the resolved replay count value.
     */
    private static int replayCount(ConsumerRecord<String, String> record, int maxReplayAttempts) {
        String value = headerValue(record, REPLAY_COUNT_HEADER);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(parsed, 0);
        } catch (Exception ignored) {
            return maxReplayAttempts + 1;
        }
    }

    /**
     * Resolves the original topic for a DLQ record using forwarded headers when present.
     *
     * @param record DLQ record containing forwarded headers.
     * @return Returns the original topic name or the DLQ record topic when missing.
     */
    private static String originalTopic(ConsumerRecord<String, String> record) {
        String original = headerValue(record, "original_topic");
        if (original != null && !original.isBlank()) {
            return original;
        }
        return record == null ? null : record.topic();
    }

    /**
     * Converts Kafka record headers into a UTF-8 string map for outbox persistence and republishing.
     *
     * @param record Kafka record containing headers to convert.
     * @return Returns a map of header keys to UTF-8 decoded string values.
     */
    private static Map<String, String> toHeaderMap(ConsumerRecord<String, String> record) {
        Map<String, String> mapped = new HashMap<>();
        if (record == null) {
            return mapped;
        }
        record.headers().forEach(header -> {
            if (header == null || header.key() == null) {
                return;
            }
            if (header.value() == null) {
                return;
            }
            mapped.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        });
        return mapped;
    }

    /**
     * Reads a Kafka header value as UTF-8 text from a record.
     *
     * @param record Kafka record containing headers.
     * @param key Header key to resolve.
     * @return Returns the header value as a string or null when missing.
     */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        if (record == null || key == null) {
            return null;
        }
        Header header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}


