package huynv.notificationservice.repository;

import huynv.notificationservice.domain.NotificationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Provides persistence operations for notification history records.
 */
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

    /**
     * Retrieves recent notification history records for a given tenant and user.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier used for per-user history filtering.
     * @param pageable Pagination and limit configuration for bounded queries.
     * @return Returns a list of recent notification history records ordered by creation time descending.
     */
    List<NotificationHistory> findByTenantIdAndUserIdOrderByCreatedAtDesc(Long tenantId, Long userId, Pageable pageable);
}

