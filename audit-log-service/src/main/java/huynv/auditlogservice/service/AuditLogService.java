package huynv.auditlogservice.service;

import huynv.auditlogservice.domain.AuditLog;
import huynv.auditlogservice.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Persists immutable audit log entries derived from consumed Kafka event envelopes.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    /**
     * Creates an audit log service backed by the audit log repository.
     *
     * @param auditLogRepository Repository used to persist audit log entries.
     * @return Initializes an audit log service instance.
     */
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    /**
     * Persists an audit log entry from the fields extracted from a Kafka event envelope.
     *
     * @param eventId Unique event identifier from the Kafka event envelope.
     * @param eventType Canonical event type string.
     * @param source Source service name from the envelope.
     * @param tenantId Tenant identifier extracted from the event payload or envelope.
     * @param userId User identifier extracted from the event payload when available.
     * @param aggregateId Aggregate identifier from the event envelope.
     * @param correlationId Correlation identifier for distributed tracing.
     * @param causationId Causation identifier linking this event to its cause.
     * @param rawPayload Original raw JSON payload string.
     * @return Performs a side effect by persisting the audit log row to the database.
     */
    @Transactional
    public void record(
            String eventId,
            String eventType,
            String source,
            Long tenantId,
            Long userId,
            String aggregateId,
            String correlationId,
            String causationId,
            String rawPayload
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(rawPayload, "rawPayload");

        AuditLog entry = new AuditLog();
        entry.setEventId(eventId);
        entry.setEventType(eventType);
        entry.setSource(source);
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setAggregateId(aggregateId);
        entry.setAggregateType(deriveAggregateType(eventType));
        entry.setCorrelationId(correlationId);
        entry.setCausationId(causationId);
        entry.setRawPayload(rawPayload);
        entry.setReceivedAt(OffsetDateTime.now());

        auditLogRepository.save(entry);

        log.info("Audit log persisted eventId={} eventType={} tenantId={} aggregateId={}",
                eventId, eventType, tenantId, aggregateId);
    }

    /**
     * Derives an aggregate type label from the event type prefix (e.g., order.created → order).
     *
     * @param eventType Canonical event type string to parse.
     * @return Returns the first segment of the event type string as the aggregate type.
     */
    private static String deriveAggregateType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "unknown";
        }
        int dotIndex = eventType.indexOf('.');
        if (dotIndex <= 0) {
            return eventType;
        }
        return eventType.substring(0, dotIndex);
    }
}

