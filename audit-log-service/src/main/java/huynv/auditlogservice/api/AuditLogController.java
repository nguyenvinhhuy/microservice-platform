package huynv.auditlogservice.api;

import huynv.auditlogservice.domain.AuditLog;
import huynv.auditlogservice.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Exposes read-only query APIs for the audit log backed by the audit_log table.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    /**
     * Creates an audit log controller backed by the audit log repository.
     *
     * @param auditLogRepository Repository used to query persisted audit log entries.
     * @return Initializes an audit log controller instance.
     */
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    /**
     * Lists audit log entries for the current tenant, with optional event type and aggregate scope filtering.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param eventType Optional event type to narrow results to a specific event kind.
     * @param aggregateId Optional aggregate identifier to narrow results to a specific entity.
     * @param page Page index starting at zero.
     * @param size Number of results per page.
     * @return Returns a paginated list of audit log responses ordered by received time descending.
     */
    @GetMapping
    public Page<AuditLogResponse> listAuditLogs(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "aggregateId", required = false) String aggregateId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<AuditLog> result;
        if (eventType != null && !eventType.isBlank()) {
            result = auditLogRepository.findByTenantIdAndEventType(tenantId, eventType, pageRequest);
        } else if (aggregateId != null && !aggregateId.isBlank()) {
            result = auditLogRepository.findByTenantIdAndAggregateId(tenantId, aggregateId, pageRequest);
        } else {
            result = auditLogRepository.findByTenantId(tenantId, pageRequest);
        }
        return result.map(AuditLogController::toResponse);
    }

    /**
     * Retrieves a single audit log entry by its surrogate identifier.
     *
     * @param tenantId Tenant identifier used to enforce data isolation.
     * @param id Surrogate database identifier of the audit log row.
     * @return Returns the matching audit log response, or 404 when not found or tenant mismatch.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("id") Long id
    ) {
        return auditLogRepository.findById(id)
                .filter(entry -> Objects.equals(entry.getTenantId(), tenantId))
                .map(AuditLogController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Lists audit log entries for a specific user within the current tenant.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to scope results to a specific user's activity.
     * @param page Page index starting at zero.
     * @param size Number of results per page.
     * @return Returns a paginated list of audit log responses for the given user.
     */
    @GetMapping("/user/{userId}")
    public Page<AuditLogResponse> getByUser(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        return auditLogRepository.findByTenantIdAndUserId(tenantId, userId, pageRequest)
                .map(AuditLogController::toResponse);
    }

    /**
     * Searches audit log entries for the current tenant by event type and optional aggregate identifier query parameters.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param eventType Optional event type filter for the search.
     * @param aggregateId Optional aggregate identifier filter for the search.
     * @param page Page index starting at zero.
     * @param size Number of results per page.
     * @return Returns a paginated list of audit log responses matching the search criteria.
     */
    @GetMapping("/search")
    public Page<AuditLogResponse> search(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "aggregateId", required = false) String aggregateId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return listAuditLogs(tenantId, eventType, aggregateId, page, size);
    }

    /**
     * Converts an AuditLog entity to an AuditLogResponse DTO.
     *
     * @param entry AuditLog entity to convert.
     * @return Returns a new AuditLogResponse populated from the entity's fields.
     */
    private static AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getEventType(),
                entry.getSource(),
                entry.getTenantId(),
                entry.getUserId(),
                entry.getAggregateId(),
                entry.getAggregateType(),
                entry.getCorrelationId(),
                entry.getCausationId(),
                entry.getReceivedAt()
        );
    }
}

