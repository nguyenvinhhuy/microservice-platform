package huynv.productviewservice.repository;

import huynv.productviewservice.model.ProductView;
import huynv.productviewservice.model.ProductViewId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence access for product read model rows with tenant-safe query methods.
 */
public interface ProductViewRepository extends JpaRepository<ProductView, ProductViewId> {

    /**
     * Loads a page of product view rows for one tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param pageable Pagination information.
     * @return Returns a page of product view rows.
     */
    Page<ProductView> findByIdTenantId(Long tenantId, Pageable pageable);
}

