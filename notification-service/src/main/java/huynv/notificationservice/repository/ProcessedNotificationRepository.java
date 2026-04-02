package huynv.notificationservice.repository;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.ProcessedNotification;
import huynv.notificationservice.domain.ProcessedNotificationId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence operations for per-channel notification idempotency markers.
 */
public interface ProcessedNotificationRepository extends JpaRepository<ProcessedNotification, ProcessedNotificationId> {

    /**
     * Determines whether an event has already been processed for a specific channel.
     *
     * @param eventId Event identifier used for idempotency.
     * @param channel Channel used for per-channel idempotency.
     * @return Returns true when the channel has already been processed for the event.
     */
    boolean existsByIdEventIdAndIdChannel(String eventId, NotificationChannelType channel);
}

