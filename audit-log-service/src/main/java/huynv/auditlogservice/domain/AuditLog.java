package huynv.auditlogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Stores an immutable audit log entry derived from a consumed Kafka event envelope.
 */
@Entity
@Table(name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_audit_log_tenant_user", columnList = "tenant_id,user_id"),
                @Index(name = "idx_audit_log_tenant_type", columnList = "tenant_id,event_type"),
                @Index(name = "idx_audit_log_received_at", columnList = "received_at")
        })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "causation_id", length = 100)
    private String causationId;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    /**
     * Creates an empty entity for JPA.
     *
     * @return Initializes an AuditLog entity instance.
     */
    public AuditLog() {
    }

    /**
     * Initializes the receivedAt timestamp before first persistence.
     *
     * @return Performs a side effect by setting receivedAt when not already assigned.
     */
    @PrePersist
    public void prePersist() {
        if (this.receivedAt == null) {
            this.receivedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    /**
     * Sets the globally unique event identifier from the Kafka event envelope.
     *
     * @param eventId Unique identifier for the originating event.
     * @return Performs a side effect by assigning the eventId field.
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    /**
     * Sets the canonical event type string from the Kafka event envelope.
     *
     * @param eventType Canonical event type (e.g., order.created).
     * @return Performs a side effect by assigning the eventType field.
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSource() {
        return source;
    }

    /**
     * Sets the source service name from the Kafka event envelope.
     *
     * @param source Name of the originating service.
     * @return Performs a side effect by assigning the source field.
     */
    public void setSource(String source) {
        this.source = source;
    }

    public Long getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant identifier extracted from the event payload.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @return Performs a side effect by assigning the tenantId field.
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the user identifier extracted from the event payload when available.
     *
     * @param userId User identifier associated with the event action.
     * @return Performs a side effect by assigning the userId field.
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Sets the aggregate identifier from the Kafka event envelope.
     *
     * @param aggregateId Identifier for the affected aggregate (e.g., orderId).
     * @return Performs a side effect by assigning the aggregateId field.
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * Sets the aggregate type derived from the event type prefix.
     *
     * @param aggregateType Type of aggregate affected (e.g., order, payment).
     * @return Performs a side effect by assigning the aggregateType field.
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation identifier for distributed trace correlation.
     *
     * @param correlationId Identifier linking related events across services.
     * @return Performs a side effect by assigning the correlationId field.
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    /**
     * Sets the causation identifier linking this event to its cause.
     *
     * @param causationId Identifier of the command or event that caused this event.
     * @return Performs a side effect by assigning the causationId field.
     */
    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    /**
     * Sets the full raw JSON payload of the received Kafka message.
     *
     * @param rawPayload Original JSON string of the event envelope.
     * @return Performs a side effect by assigning the rawPayload field.
     */
    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    /**
     * Sets the timestamp at which this audit log entry was received and persisted.
     *
     * @param receivedAt Timestamp recorded at ingestion time.
     * @return Performs a side effect by assigning the receivedAt field.
     */
    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}

