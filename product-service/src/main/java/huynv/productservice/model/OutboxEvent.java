package huynv.productservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores immutable integration events in the same transaction as product mutations.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_product_outbox_status_created", columnList = "status,created_at"),
                @Index(name = "idx_product_outbox_status_next", columnList = "status,next_attempt_at"),
                @Index(name = "idx_product_outbox_aggregate", columnList = "aggregate_type,aggregate_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Column(name = "type", nullable = false, length = 120)
    private String type;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Initializes immutable metadata and retry fields when the outbox row is first persisted.
     *
     * @return persists default status and audit timestamps for reliable publishing.
     */
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.eventId == null) {
            this.eventId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = now;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates audit timestamp whenever the outbox row changes.
     *
     * @return keeps modification time for scheduler diagnostics.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

