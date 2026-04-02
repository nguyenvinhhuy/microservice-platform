package huynv.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists a delivery attempt outcome for auditing and user-facing history retrieval.
 */
@Entity
@Table(name = "notification_history")
public class NotificationHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected NotificationHistory() {
    }

    /**
     * Creates a new notification history record instance.
     *
     * @param id Record identifier.
     * @param userId User identifier for the intended recipient when available.
     * @param tenantId Tenant identifier used for data isolation.
     * @param type Notification type describing the business intent.
     * @param channel Delivery channel used for this attempt.
     * @param payload Serialized payload persisted for auditing and debugging.
     * @param status Delivery status for this attempt.
     * @param createdAt Timestamp when the record was created.
     * @return Returns a fully initialized NotificationHistory entity.
     */
    public NotificationHistory(UUID id,
                               Long userId,
                               Long tenantId,
                               String eventId,
                               String type,
                               String channel,
                               String priority,
                               String provider,
                               String payload,
                               NotificationStatus status,
                               OffsetDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = userId;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.eventId = eventId;
        this.type = Objects.requireNonNull(type, "type");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.priority = priority;
        this.provider = provider;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Creates a new notification history record with a generated identifier and current timestamp.
     *
     * @param userId User identifier for the intended recipient when available.
     * @param tenantId Tenant identifier used for data isolation.
     * @param type Notification type describing the business intent.
     * @param channel Delivery channel used for this attempt.
     * @param payload Serialized payload persisted for auditing and debugging.
     * @param status Delivery status for this attempt.
     * @return Returns a new NotificationHistory entity with generated id and createdAt.
     */
    public static NotificationHistory create(Long userId,
                                             Long tenantId,
                                             String type,
                                             String channel,
                                             String payload,
                                             NotificationStatus status) {
        return new NotificationHistory(
                UUID.randomUUID(),
                userId,
                tenantId,
                null,
                type,
                channel,
                null,
                null,
                payload,
                status,
                OffsetDateTime.now()
        );
    }

    /**
     * Creates a new notification history record with additional correlation metadata.
     *
     * @param userId User identifier for the intended recipient when available.
     * @param tenantId Tenant identifier used for data isolation.
     * @param eventId Event identifier used for end-to-end correlation and idempotency analysis.
     * @param type Notification type describing the business intent.
     * @param channel Delivery channel used for this attempt.
     * @param priority Priority value used for scheduling and operational reasoning.
     * @param provider Provider name used for external delivery.
     * @param payload Serialized payload persisted for auditing and debugging.
     * @param status Delivery status for this attempt.
     * @return Returns a new NotificationHistory entity with generated id and createdAt.
     */
    public static NotificationHistory createWithMetadata(Long userId,
                                                         Long tenantId,
                                                         String eventId,
                                                         String type,
                                                         String channel,
                                                         String priority,
                                                         String provider,
                                                         String payload,
                                                         NotificationStatus status) {
        return new NotificationHistory(
                UUID.randomUUID(),
                userId,
                tenantId,
                eventId,
                type,
                channel,
                priority,
                provider,
                payload,
                status,
                OffsetDateTime.now()
        );
    }

    /**
     * Returns the record identifier.
     *
     * @return Returns the notification history record identifier.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the intended recipient user identifier when available.
     *
     * @return Returns the user identifier or null when unknown.
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Returns the tenant identifier used for data isolation.
     *
     * @return Returns the tenant identifier.
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * Returns the event identifier when available for correlation and idempotency analysis.
     *
     * @return Returns the event identifier or null when not recorded.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the notification type string.
     *
     * @return Returns the notification type string.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the delivery channel string.
     *
     * @return Returns the delivery channel string.
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Returns the priority value recorded for the delivery attempt when available.
     *
     * @return Returns the priority value or null when not recorded.
     */
    public String getPriority() {
        return priority;
    }

    /**
     * Returns the provider name recorded for the delivery attempt when available.
     *
     * @return Returns the provider name or null when not recorded.
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Returns the persisted JSON payload.
     *
     * @return Returns the serialized payload persisted for auditing.
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Returns the delivery status.
     *
     * @return Returns the delivery status.
     */
    public NotificationStatus getStatus() {
        return status;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return Returns the timestamp when the record was created.
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
