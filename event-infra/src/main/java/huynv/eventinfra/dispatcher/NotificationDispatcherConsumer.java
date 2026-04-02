package huynv.eventinfra.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.eventinfra.util.MdcUtil;
import huynv.eventinfra.util.TraceHeaderUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Consumes priority notification jobs and forwards them to per-channel worker topics.
 */
@Component
@ConditionalOnExpression("${notification.dispatcher.enabled:false} && ${notification.dispatch.enabled:true}")
public class NotificationDispatcherConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcherConsumer.class);

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final KafkaOutboxService outboxService;

    /**
     * Creates a dispatcher consumer that forwards jobs to channel-specific topics.
     *
     * @param properties Notification properties containing topic names.
     * @param objectMapper ObjectMapper used to parse and serialize job payloads.
     * @param outboxService Outbox service used to persist forwarding for asynchronous Kafka publishing.
     * @return Initializes a dispatcher consumer.
     */
    public NotificationDispatcherConsumer(NotificationProperties properties, ObjectMapper objectMapper, KafkaOutboxService outboxService) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
    }

    /**
     * Consumes high priority jobs and forwards them to channel workers.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by forwarding jobs to channel worker topics.
     */
    @KafkaListener(
            id = "notification-dispatcher-high",
            topics = "${notification.dispatcher.high-topic:notification.high}",
            groupId = "${notification.dispatcher.dispatcher-group-id:notification-dispatcher}",
            concurrency = "${notification.dispatcher.priority-weights.high:5}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onHigh(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forward(record, acknowledgment);
    }

    /**
     * Consumes normal priority jobs and forwards them to channel workers.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by forwarding jobs to channel worker topics.
     */
    @KafkaListener(
            id = "notification-dispatcher-normal",
            topics = "${notification.dispatcher.normal-topic:notification.normal}",
            groupId = "${notification.dispatcher.dispatcher-group-id:notification-dispatcher}",
            concurrency = "${notification.dispatcher.priority-weights.normal:3}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onNormal(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forward(record, acknowledgment);
    }

    /**
     * Consumes low priority jobs and forwards them to channel workers.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by forwarding jobs to channel worker topics.
     */
    @KafkaListener(
            id = "notification-dispatcher-low",
            topics = "${notification.dispatcher.low-topic:notification.low}",
            groupId = "${notification.dispatcher.dispatcher-group-id:notification-dispatcher}",
            concurrency = "${notification.dispatcher.priority-weights.low:1}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onLow(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        forward(record, acknowledgment);
    }

    /**
     * Forwards a priority job record to the appropriate channel worker topic using the transactional outbox.
     *
     * @param record Kafka record containing a serialized notification job.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by persisting an outbox row that will publish the job to a channel topic.
     */
    private void forward(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(acknowledgment, "acknowledgment");

            NotificationJob job = objectMapper.readValue(record.value(), NotificationJob.class);
            Map<String, String> mdc = new HashMap<>();
            mdc.put("eventId", job.eventId());
            mdc.put("tenantId", job.tenantId() == null ? null : job.tenantId().toString());
            mdc.put("userId", job.userId() == null ? null : job.userId().toString());
            mdc.put("channel", job.channel());
            mdc.put("priority", job.priority());
            MdcUtil.putAll(mdc);

            String topic = toChannelTopic(job);
            Map<String, String> headers = new HashMap<>();
            headers.put("eventId", job.eventId());
            headers.put("eventType", job.eventType());
            TraceHeaderUtil.putTraceHeaders(headers, job.traceId(), job.correlationId(), job.eventId());
            headers.put("tenantId", job.tenantId() == null ? null : job.tenantId().toString());
            headers.put("orderId", job.orderId());
            headers.put("channel", job.channel());
            headers.put("priority", job.priority());
            headers.put("sourceTopic", record.topic());
            outboxService.enqueue(topic, partitionKey(job), record.value(), headers, KafkaOutboxPurpose.DISPATCH, OffsetDateTime.now());
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.warn("Dispatcher forward failed topic={} partition={} offset={} error={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getMessage());
            throw new IllegalStateException("Dispatcher forward failed.", ex);
        } finally {
            MdcUtil.clear();
        }
    }

    private String toChannelTopic(NotificationJob job) {
        if (job.channel() == null) {
            throw new IllegalArgumentException("Notification job channel is required.");
        }
        return switch (job.channel().trim().toUpperCase()) {
            case "EMAIL" -> properties.getDispatcher().getEmailTopic();
            case "SMS" -> properties.getDispatcher().getSmsTopic();
            case "PUSH" -> properties.getDispatcher().getPushTopic();
            default -> throw new IllegalArgumentException("Unsupported notification channel: " + job.channel() + ".");
        };
    }

    /**
     * Builds a partition key that keeps jobs for the same aggregate aligned on the same Kafka partition when possible.
     *
     * @param job Job used to derive the key.
     * @return Returns a partition key suitable for Kafka ordering within a partition.
     */
    private static String partitionKey(NotificationJob job) {
        String tenant = job.tenantId() == null ? "unknown" : job.tenantId().toString();
        if (job.orderId() != null && !job.orderId().isBlank()) {
            return tenant + ":" + job.orderId().trim();
        }
        if (job.userId() != null) {
            return tenant + ":user:" + job.userId();
        }
        return tenant + ":" + (job.eventId() == null ? "unknown" : job.eventId());
    }
}

