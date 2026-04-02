package huynv.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Tracks per-channel delivery idempotency to prevent duplicate external sends for the same event.
 */
@Entity
@Table(name = "processed_notifications")
public class ProcessedNotification {

    @EmbeddedId
    private ProcessedNotificationId id;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedNotification() {
    }

    /**
     * Creates a new per-channel idempotency marker.
     *
     * @param id Composite identifier containing eventId and channel.
     * @param processedAt Timestamp when the channel was processed.
     * @return Returns a fully initialized ProcessedNotification entity.
     */
    public ProcessedNotification(ProcessedNotificationId id, OffsetDateTime processedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
    }

    /**
     * Creates a new processed marker using the current timestamp.
     *
     * @param eventId Event identifier used for idempotency.
     * @param channel Channel identifier used for per-channel idempotency.
     * @return Returns a new ProcessedNotification entity.
     */
    public static ProcessedNotification create(String eventId, NotificationChannelType channel) {
        return new ProcessedNotification(new ProcessedNotificationId(eventId, channel), OffsetDateTime.now());
    }

    /**
     * Returns the composite identifier.
     *
     * @return Returns the composite identifier.
     */
    public ProcessedNotificationId getId() {
        return id;
    }

    /**
     * Returns the timestamp when the channel was processed.
     *
     * @return Returns the processedAt timestamp.
     */
    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}

