package huynv.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Stores payment integration events for reliable at-least-once publishing using the outbox pattern.
 */
@Entity
@Table(name = "payment_outbox")
public class PaymentOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "span_id", length = 32)
    private String spanId;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentOutboxStatus status;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Creates an empty JPA entity required by Hibernate.
     *
     * @return Initializes an empty outbox entity instance.
     */
    protected PaymentOutbox() {
    }

    /**
     * Creates a new outbox record in unpublished state.
     *
     * @param aggregateType Aggregate type name for routing and filtering.
     * @param aggregateId Aggregate identifier for partitioning and correlation.
     * @param eventType Event type name for schema versioning and consumers.
     * @param payload JSON payload for Kafka value publishing.
     * @param createdAt Creation timestamp for ordering and retries.
     * @return Initializes a new unpublished outbox record.
     */
    public static PaymentOutbox unpublished(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            String correlationId,
            String traceId,
            String spanId,
            OffsetDateTime createdAt
    ) {
        PaymentOutbox outbox = new PaymentOutbox();
        outbox.aggregateType = aggregateType;
        outbox.aggregateId = aggregateId;
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.correlationId = correlationId;
        outbox.traceId = traceId;
        outbox.spanId = spanId;
        outbox.createdAt = createdAt;
        outbox.published = false;
        outbox.status = PaymentOutboxStatus.NEW;
        outbox.processingStartedAt = null;
        outbox.publishAttempts = 0;
        outbox.nextAttemptAt = null;
        outbox.lastError = null;
        return outbox;
    }

    /**
     * Marks the outbox record as claimed for publishing in a separate transaction.
     *
     * @param now Claim timestamp for operational visibility and stale-claim detection.
     * @return Updates status to PROCESSING for two-phase publishing.
     */
    public void markProcessing(OffsetDateTime now) {
        this.status = PaymentOutboxStatus.PROCESSING;
        this.processingStartedAt = now;
    }

    /**
     * Marks the outbox record as published.
     *
     * @param publishedAt Publish timestamp.
     * @return Updates published state for this outbox record.
     */
    public void markPublished(OffsetDateTime publishedAt) {
        this.published = true;
        this.status = PaymentOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.processingStartedAt = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Records a failed publish attempt and schedules a future retry.
     *
     * @param nextAttemptAt Next attempt time for retry scheduling.
     * @param error Error message to store for diagnostics.
     * @return Updates attempts count and retry scheduling fields.
     */
    public void markFailedAttempt(OffsetDateTime nextAttemptAt, String error) {
        this.publishAttempts = this.publishAttempts + 1;
        this.status = PaymentOutboxStatus.NEW;
        this.processingStartedAt = null;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
    }

    /**
     * Returns outbox identifier.
     *
     * @return Outbox record id.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns aggregate type.
     *
     * @return Aggregate type name.
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * Returns aggregate id.
     *
     * @return Aggregate identifier.
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Returns event type.
     *
     * @return Event type name.
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Returns JSON payload.
     *
     * @return Kafka event payload.
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Returns the correlation identifier for the outbox event when available.
     *
     * @return Correlation identifier or null when not set.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns the trace identifier for the outbox event when available.
     *
     * @return Trace identifier or null when not set.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Returns the span identifier for the outbox event when available.
     *
     * @return Span identifier or null when not set.
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * Returns created timestamp.
     *
     * @return Created timestamp.
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns current outbox processing status.
     *
     * @return Outbox status.
     */
    public PaymentOutboxStatus getStatus() {
        return status;
    }

    /**
     * Returns published flag.
     *
     * @return True when published.
     */
    public boolean isPublished() {
        return published;
    }

    /**
     * Returns publish attempts count.
     *
     * @return Number of publish attempts.
     */
    public int getPublishAttempts() {
        return publishAttempts;
    }

    /**
     * Returns next attempt timestamp.
     *
     * @return Next attempt time or null.
     */
    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    /**
     * Returns the last publish error message when present.
     *
     * @return Last publish error message or null when not set.
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Returns the published timestamp when present.
     *
     * @return Published timestamp or null when not published.
     */
    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    /**
     * Returns the processing started timestamp when present.
     *
     * @return Processing started timestamp or null when not processing.
     */
    public OffsetDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }
}
