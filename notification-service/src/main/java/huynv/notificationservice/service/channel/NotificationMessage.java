package huynv.notificationservice.service.channel;

import huynv.notificationservice.domain.NotificationType;

import java.util.Map;

/**
 * Represents a normalized notification message to be delivered via one or more channels.
 *
 * @param notificationType Notification type derived from the inbound platform event.
 * @param tenantId Tenant identifier used for multi-tenant isolation.
 * @param userId Recipient user identifier when available.
 * @param subject Human-readable subject line used by channels that support it.
 * @param templateName Template name used for rendering channel payloads.
 * @param templateModel Template model values used for rendering templates.
 * @param rawEventPayload Raw JSON payload stored for auditing and debugging.
 */
public record NotificationMessage(
        NotificationType notificationType,
        Long tenantId,
        Long userId,
        String subject,
        String templateName,
        Map<String, Object> templateModel,
        String rawEventPayload
) {
}

