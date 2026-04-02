package huynv.eventinfra.dispatcher;

import java.util.List;
import java.util.Map;

/**
 * Represents a per-channel notification job flowing through priority queues and channel workers.
 *
 * @param eventId Original upstream event identifier.
 * @param eventType Original upstream event type.
 * @param tenantId Tenant identifier used for multi-tenant isolation.
 * @param userId Recipient user identifier.
 * @param orderId Order identifier when available for correlation.
 * @param notificationType Normalized notification type.
 * @param channel Delivery channel for this job.
 * @param priority Scheduling priority for this job.
 * @param subject Subject used by channels that support it.
 * @param templateName Template name used for rendering channel payloads.
 * @param templateModel Template model values used for rendering templates.
 * @param rawEventPayload Raw upstream event payload used for auditing.
 * @param recipientEmail Resolved email address used for email delivery.
 * @param recipientPhoneNumber Resolved phone number used for SMS delivery.
 * @param recipientPushTokens Resolved push tokens used for push delivery.
 * @param traceId Trace identifier for correlation.
 * @param correlationId Correlation identifier for request tracing.
 */
public record NotificationJob(
        String eventId,
        String eventType,
        Long tenantId,
        Long userId,
        String orderId,
        String notificationType,
        String channel,
        String priority,
        String subject,
        String templateName,
        Map<String, Object> templateModel,
        String rawEventPayload,
        String recipientEmail,
        String recipientPhoneNumber,
        List<String> recipientPushTokens,
        String traceId,
        String correlationId
) {
}


