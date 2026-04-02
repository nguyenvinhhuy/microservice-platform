package huynv.notificationservice.service;

import huynv.notificationservice.domain.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a normalized notification intent derived from an upstream platform event before channel expansion.
 *
 * @param eventId Upstream event identifier.
 * @param eventType Upstream event type.
 * @param tenantId Tenant identifier used for multi-tenant isolation.
 * @param userId Recipient user identifier when present in the upstream payload.
 * @param orderId Order identifier when present in the upstream payload.
 * @param notificationType Normalized notification type describing intent.
 * @param subject Subject used by channels that support it.
 * @param templateName Template name used for rendering channel payloads.
 * @param templateModel Template model values used for rendering templates.
 * @param rawEventPayload Raw upstream event payload used for auditing.
 * @param traceId Trace identifier for correlation.
 * @param correlationId Correlation identifier for request tracing.
 */
public record NotificationIntent(
        String eventId,
        String eventType,
        Long tenantId,
        Long userId,
        UUID orderId,
        NotificationType notificationType,
        String subject,
        String templateName,
        Map<String, Object> templateModel,
        String rawEventPayload,
        String traceId,
        String correlationId
) {
}

