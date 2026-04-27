package huynv.auditlogservice.repository;

import huynv.auditlogservice.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides tenant-aware persistence access for audit log entries.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Loads a page of audit log entries for a given tenant.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param pageable Pagination information for result slicing.
     * @return Returns a page of audit log entries belonging to the tenant.
     */
    Page<AuditLog> findByTenantId(Long tenantId, Pageable pageable);

    /**
     * Loads a page of audit log entries for a given user within a tenant.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to scope results per user.
     * @param pageable Pagination information for result slicing.
     * @return Returns a page of audit log entries belonging to the tenant user.
     */
    Page<AuditLog> findByTenantIdAndUserId(Long tenantId, Long userId, Pageable pageable);

    /**
     * Loads a page of audit log entries filtered by tenant and event type prefix match.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param eventType Exact event type string to match.
     * @param pageable Pagination information for result slicing.
     * @return Returns a page of audit log entries matching the tenant and event type.
     */
    Page<AuditLog> findByTenantIdAndEventType(Long tenantId, String eventType, Pageable pageable);

    /**
     * Loads a page of audit log entries filtered by tenant and aggregate identifier.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param aggregateId Aggregate identifier to scope the search.
     * @param pageable Pagination information for result slicing.
     * @return Returns a page of audit log entries matching the tenant and aggregate.
     */
    Page<AuditLog> findByTenantIdAndAggregateId(Long tenantId, String aggregateId, Pageable pageable);
}

