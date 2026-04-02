package huynv.notificationservice.dto;

import huynv.notificationservice.domain.NotificationHistory;
import huynv.notificationservice.domain.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Defines the API response model for notification history records.
 *
 * @param id Notification history record identifier.
 * @param userId Intended recipient user identifier when known.
 * @param tenantId Tenant identifier used for data isolation.
 * @param type Notification type describing the business intent.
 * @param channel Delivery channel used for this attempt.
 * @param status Delivery status for this attempt.
 * @param createdAt Timestamp when the record was created.
 */
public record NotificationHistoryResponse(
        UUID id,
        Long userId,
        Long tenantId,
        String type,
        String channel,
        NotificationStatus status,
        OffsetDateTime createdAt
) {

    /**
     * Maps a NotificationHistory entity into a DTO suitable for API responses.
     *
     * @param history Persisted notification history record.
     * @return Returns a NotificationHistoryResponse containing user-safe fields.
     */
    public static NotificationHistoryResponse from(NotificationHistory history) {
        return new NotificationHistoryResponse(
                history.getId(),
                history.getUserId(),
                history.getTenantId(),
                history.getType(),
                history.getChannel(),
                history.getStatus(),
                history.getCreatedAt()
        );
    }
}

