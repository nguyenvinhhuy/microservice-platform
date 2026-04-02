package huynv.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

/**
 * Defines the composite identifier for per-channel notification idempotency markers.
 */
@Embeddable
public class ProcessedNotificationId implements Serializable {

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannelType channel;

    protected ProcessedNotificationId() {
    }

    /**
     * Creates a composite identifier for an event and channel.
     *
     * @param eventId Event identifier used for idempotency.
     * @param channel Channel identifier used for per-channel idempotency.
     * @return Returns a fully initialized ProcessedNotificationId value object.
     */
    public ProcessedNotificationId(String eventId, NotificationChannelType channel) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    /**
     * Returns the event identifier.
     *
     * @return Returns the event identifier.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the channel identifier.
     *
     * @return Returns the channel identifier.
     */
    public NotificationChannelType getChannel() {
        return channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessedNotificationId that)) {
            return false;
        }
        return eventId.equals(that.eventId) && channel == that.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, channel);
    }
}

