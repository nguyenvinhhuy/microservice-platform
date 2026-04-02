package huynv.eventinfra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.eventinfra.config.NotificationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the transactional outbox pattern for Kafka publishing.
 */
@Service
public class KafkaOutboxService {

    private static final Collection<KafkaOutboxStatus> ELIGIBLE_STATUSES = List.of(KafkaOutboxStatus.PENDING, KafkaOutboxStatus.FAILED);

    private final NotificationProperties properties;
    private final KafkaOutboxRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Creates an outbox service backed by JPA persistence.
     *
     * @param properties Notification properties used to enforce retry budgets and DLQ routing.
     * @param repository Repository used to persist and claim outbox messages.
     * @param objectMapper ObjectMapper used to serialize headers deterministically.
     * @return Initializes a Kafka outbox service instance.
     */
    public KafkaOutboxService(NotificationProperties properties, KafkaOutboxRepository repository, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Enqueues a message for asynchronous Kafka publishing after the current transaction commits.
     *
     * @param topic Target Kafka topic.
     * @param messageKey Kafka partitioning key.
     * @param payload Serialized payload value.
     * @param headers Kafka headers to persist for later injection.
     * @param purpose Purpose label used for backlog monitoring.
     * @param dueAt Timestamp after which publishing may be attempted.
     * @return Returns the persisted outbox message identifier.
     */
    public UUID enqueue(String topic,
                        String messageKey,
                        String payload,
                        Map<String, String> headers,
                        KafkaOutboxPurpose purpose,
                        OffsetDateTime dueAt) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(dueAt, "dueAt");

        UUID id = UUID.randomUUID();
        KafkaOutboxMessage message = new KafkaOutboxMessage(
                id,
                topic,
                messageKey,
                payload,
                toHeadersJson(headers),
                purpose,
                dueAt
        );
        repository.save(message);
        return id;
    }

    /**
     * Claims a batch of due outbox messages for publishing.
     *
     * @param batchSize Maximum number of messages to claim.
     * @return Returns due messages marked as PROCESSING within the current transaction.
     */
    @Transactional
    public List<KafkaOutboxMessage> claimDueBatch(int batchSize) {
        int safeSize = Math.max(1, Math.min(batchSize, 1000));
        List<KafkaOutboxMessage> due = repository.findDueForUpdate(ELIGIBLE_STATUSES, OffsetDateTime.now(), PageRequest.of(0, safeSize));
        for (KafkaOutboxMessage message : due) {
            message.setStatus(KafkaOutboxStatus.PROCESSING);
        }
        if (!due.isEmpty()) {
            repository.saveAll(due);
        }
        return due;
    }

    /**
     * Claims a batch of due outbox messages for publishing while also reclaiming stale PROCESSING rows.
     *
     * @param batchSize Maximum number of messages to claim.
     * @param processingTimeout Duration after which PROCESSING rows are treated as stale and eligible for re-claim.
     * @return Returns due or stale messages marked as PROCESSING within the current transaction.
     */
    @Transactional
    public List<KafkaOutboxMessage> claimDueBatchWithLease(int batchSize, Duration processingTimeout) {
        Objects.requireNonNull(processingTimeout, "processingTimeout");
        int safeSize = Math.max(1, Math.min(batchSize, 1000));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime staleBefore = now.minus(processingTimeout);
        List<KafkaOutboxMessage> due = repository.findDueOrStaleProcessingForUpdate(
                ELIGIBLE_STATUSES,
                KafkaOutboxStatus.PROCESSING,
                now,
                staleBefore,
                PageRequest.of(0, safeSize)
        );
        for (KafkaOutboxMessage message : due) {
            message.setStatus(KafkaOutboxStatus.PROCESSING);
        }
        if (!due.isEmpty()) {
            repository.saveAll(due);
        }
        return due;
    }

    /**
     * Marks an outbox message as successfully published.
     *
     * @param id Outbox message identifier.
     * @return Performs a side effect by transitioning the outbox message to SENT.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID id) {
        Objects.requireNonNull(id, "id");
        KafkaOutboxMessage message = repository.findById(id).orElseThrow();
        message.setStatus(KafkaOutboxStatus.SENT);
        message.setLastError(null);
        message.setPublishedAt(OffsetDateTime.now());
        repository.save(message);
    }

    /**
     * Marks an outbox message as failed and schedules its next attempt time.
     *
     * @param id Outbox message identifier.
     * @param error Error text used for diagnostics.
     * @param nextDelay Delay applied before the next attempt.
     * @return Performs a side effect by transitioning the outbox message to FAILED and updating scheduling fields.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String error, Duration nextDelay) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nextDelay, "nextDelay");
        KafkaOutboxMessage message = repository.findById(id).orElseThrow();
        int nextRetryCount = message.getRetryCount() + 1;
        int maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
        if (nextRetryCount >= maxAttempts) {
            message.setStatus(KafkaOutboxStatus.DLQED);
            message.setRetryCount(nextRetryCount);
            message.setLastError(trim(error, 500));
            message.setDueAt(OffsetDateTime.now());
            repository.save(message);

            if (properties.getKafka().getDlqTopic().equals(message.getTopic()) || message.getPurpose() == KafkaOutboxPurpose.DLQ) {
                return;
            }

            Map<String, String> headers = new HashMap<>(parseHeaders(message.getHeadersJson()));
            headers.putIfAbsent("original_topic", message.getTopic());
            headers.put("dlq_reason", "outbox_publish_exhausted");
            headers.put("exception_message", trim(error, 1024));
            headers.put("outbox_message_id", id.toString());
            enqueue(properties.getKafka().getDlqTopic(), message.getMessageKey(), message.getPayload(), headers, KafkaOutboxPurpose.DLQ, OffsetDateTime.now());
            return;
        }

        message.setStatus(KafkaOutboxStatus.FAILED);
        message.setRetryCount(nextRetryCount);
        message.setLastError(trim(error, 500));
        message.setDueAt(OffsetDateTime.now().plus(nextDelay));
        repository.save(message);
    }

    /**
     * Returns the total outbox backlog size for monitoring purposes.
     *
     * @return Returns the number of outbox rows waiting for publishing.
     */
    public long backlogSize() {
        return repository.countByStatusIn(ELIGIBLE_STATUSES);
    }

    /**
     * Returns the backlog size for a specific outbox purpose.
     *
     * @param purpose Outbox message purpose to count.
     * @return Returns the number of outbox rows matching the purpose and eligible statuses.
     */
    public long backlogSize(KafkaOutboxPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        return repository.countByPurposeAndStatusIn(purpose, ELIGIBLE_STATUSES);
    }

    /**
     * Deserializes persisted header JSON into a map for Kafka producer injection.
     *
     * @param headersJson Persisted header JSON string.
     * @return Returns a header map or an empty map when input is blank or invalid.
     */
    public Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(headersJson, Map.class);
            Map<String, String> mapped = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object value = entry.getValue();
                mapped.put(entry.getKey(), value == null ? null : String.valueOf(value));
            }
            return mapped;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    /**
     * Loads a single outbox message by identifier.
     *
     * @param id Outbox message identifier.
     * @return Returns the message when present.
     */
    public Optional<KafkaOutboxMessage> find(UUID id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id);
    }

    private String toHeadersJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Kafka outbox headers.", ex);
        }
    }

    private static String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}

