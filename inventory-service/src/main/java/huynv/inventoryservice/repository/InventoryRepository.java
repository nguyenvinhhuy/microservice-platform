package huynv.inventoryservice.repository;

import huynv.inventoryservice.domain.Inventory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Inventory entities.
 * All queries are tenant-aware to enforce data isolation.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Finds all inventory records for a given list of product IDs and a specific tenant.
     * This is a key method for ensuring that stock checks are performed only within the correct tenant's data.
     *
     * @param productIds The list of product IDs to look up.
     * @param tenantId The ID of the tenant who owns the inventory.
     * @return Returns a list of Inventory objects matching the criteria.
     */
    List<Inventory> findAllByProductIdInAndTenantId(List<Long> productIds, Long tenantId);

    /**
     * Atomically reserves stock by incrementing reservedStock only when enough available stock exists.
     *
     * @param tenantId Tenant identifier owning the inventory row.
     * @param productId Product identifier whose stock is being reserved.
     * @param quantity Quantity requested to reserve.
     * @return Returns number of updated rows, where 1 indicates success and 0 indicates insufficient stock or missing row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory i
            set i.reservedStock = i.reservedStock + :quantity,
                i.version = i.version + 1
            where i.tenantId = :tenantId
              and i.productId = :productId
              and (i.totalStock - i.reservedStock) >= :quantity
            """)
    int reserveStockIfAvailable(@Param("tenantId") Long tenantId,
                                @Param("productId") Long productId,
                                @Param("quantity") Integer quantity);
}
