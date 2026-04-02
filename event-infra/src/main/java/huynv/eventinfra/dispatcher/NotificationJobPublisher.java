package huynv.eventinfra.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.eventinfra.util.TraceHeaderUtil;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes per-channel notification jobs to priority Kafka topics for dispatcher processing.
 */
@Component
public class NotificationJobPublisher {

    private final KafkaOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;

    /**
     * Creates a job publisher that serializes jobs and publishes them to priority topics.
     *
     * @param outboxService Outbox service used to persist publishes for asynchronous delivery.
     * @param objectMapper ObjectMapper used to serialize job payloads.
     * @param properties Notification properties containing topic names.
     * @return Initializes a notification job publisher.
     */
    public NotificationJobPublisher(KafkaOutboxService outboxService, ObjectMapper objectMapper, NotificationProperties properties) {
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Publishes a notification job to the configured priority topic.
     *
     * @param job Notification job to publish.
     * @return Performs a side effect by publishing the job to Kafka.
     */
    public void publish(NotificationJob job) {
        Objects.requireNonNull(job, "job");
        String topic = toTopic(job.priority());
        try {
            String payload = objectMapper.writeValueAsString(job);
            Map<String, String> headers = new HashMap<>();
            headers.put("eventId", job.eventId());
            headers.put("eventType", job.eventType());
            TraceHeaderUtil.putTraceHeaders(headers, job.traceId(), job.correlationId(), job.eventId());
            headers.put("tenantId", job.tenantId() == null ? null : job.tenantId().toString());
            headers.put("orderId", job.orderId());
            headers.put("channel", job.channel());
            headers.put("priority", job.priority());
            outboxService.enqueue(topic, partitionKey(job), payload, headers, KafkaOutboxPurpose.DISPATCH, OffsetDateTime.now());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish notification job eventId=" + job.eventId() + ".", ex);
        }
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

    private String toTopic(String priority) {
        if (priority == null) {
            return properties.getDispatcher().getNormalTopic();
        }
        return switch (priority.trim().toUpperCase()) {
            case "HIGH" -> properties.getDispatcher().getHighTopic();
            case "LOW" -> properties.getDispatcher().getLowTopic();
            default -> properties.getDispatcher().getNormalTopic();
        };
    }
}

