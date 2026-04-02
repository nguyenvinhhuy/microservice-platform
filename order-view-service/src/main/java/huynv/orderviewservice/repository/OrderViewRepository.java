package huynv.orderviewservice.repository;

import huynv.orderviewservice.model.OrderView;
import huynv.orderviewservice.model.OrderViewId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence access for order read model rows with tenant-safe query methods.
 */
public interface OrderViewRepository extends JpaRepository<OrderView, OrderViewId> {

    /**
     * Loads a page of orders for one tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param pageable Pagination information.
     * @return Returns a page of order view rows.
     */
    Page<OrderView> findByIdTenantId(Long tenantId, Pageable pageable);

    /**
     * Loads a page of orders for one user in one tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param userId User identifier used to scope per-user order views.
     * @param pageable Pagination information.
     * @return Returns a page of order view rows.
     */
    Page<OrderView> findByIdTenantIdAndUserId(Long tenantId, Long userId, Pageable pageable);
}

