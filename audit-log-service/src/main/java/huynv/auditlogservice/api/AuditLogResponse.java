package huynv.auditlogservice.api;

import java.time.OffsetDateTime;

/**
 * Represents a single audit log entry returned from the query API.
 *
 * @param id Surrogate database identifier for the audit log row.
 * @param eventId Globally unique event identifier from the Kafka envelope.
 * @param eventType Canonical event type string.
 * @param source Name of the originating service.
 * @param tenantId Tenant identifier for the event.
 * @param userId User identifier associated with the event action, if available.
 * @param aggregateId Identifier of the affected aggregate.
 * @param aggregateType Type of the aggregate derived from the event type prefix.
 * @param correlationId Correlation identifier for distributed tracing.
 * @param causationId Causation identifier linking this event to its cause.
 * @param receivedAt Timestamp at which the audit entry was ingested.
 */
public record AuditLogResponse(
        Long id,
        String eventId,
        String eventType,
        String source,
        Long tenantId,
        Long userId,
        String aggregateId,
        String aggregateType,
        String correlationId,
        String causationId,
        OffsetDateTime receivedAt
) {
}

