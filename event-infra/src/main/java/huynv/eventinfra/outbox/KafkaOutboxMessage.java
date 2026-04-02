package huynv.eventinfra.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists Kafka messages in a local database transaction so publishing can be retried safely after commit.
 */
@Entity
@Table(name = "kafka_outbox")
public class KafkaOutboxMessage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", length = 200)
    private String messageKey;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Lob
    @Column(name = "headers_json")
    private String headersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private KafkaOutboxPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KafkaOutboxStatus status;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected KafkaOutboxMessage() {
    }

    /**
     * Creates a new outbox message for later publishing.
     *
     * @param id Stable identifier used for idempotent publisher state transitions.
     * @param topic Kafka topic that will receive the message.
     * @param messageKey Kafka partitioning key used to preserve per-aggregate ordering when possible.
     * @param payload Serialized message value payload.
     * @param headersJson JSON representation of Kafka headers for propagation and auditing.
     * @param purpose Purpose label used for metrics and routing diagnostics.
     * @param dueAt Timestamp after which the publisher may attempt sending.
     * @return Returns a fully initialized KafkaOutboxMessage entity.
     */
    public KafkaOutboxMessage(UUID id,
                              String topic,
                              String messageKey,
                              String payload,
                              String headersJson,
                              KafkaOutboxPurpose purpose,
                              OffsetDateTime dueAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.messageKey = messageKey;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.headersJson = headersJson;
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
        this.status = KafkaOutboxStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Initializes required timestamps before the row is first persisted.
     *
     * @return Persists default state transitions and timestamps for reliable publishing behavior.
     */
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.status == null) {
            this.status = KafkaOutboxStatus.PENDING;
        }
        if (this.dueAt == null) {
            this.dueAt = now;
        }
    }

    /**
     * Updates the last-modified timestamp whenever the row is updated.
     *
     * @return Performs a side effect by updating the updatedAt timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public KafkaOutboxPurpose getPurpose() {
        return purpose;
    }

    public KafkaOutboxStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setStatus(KafkaOutboxStatus status) {
        this.status = status;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}


