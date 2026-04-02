package huynv.inventoryservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Represents the inventory for a specific product, including total, available, and reserved stock.
@Entity
@Table(
        name = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_tenant_product", columnNames = {"tenant_id", "product_id"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique identifier for the product.
    @Column(nullable = false)
    private Long productId;

    // The total stock available for the product.
    @Column(nullable = false)
    private Integer totalStock;

    // The stock currently reserved for pending orders.
    @Column(nullable = false)
    private Integer reservedStock;

    // The tenant ID for multi-tenancy.
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Version for optimistic locking.
    @Version
    private Long version;

    // Calculates the currently available stock (total - reserved).
    /**
     * getAvailableStock operation.
     *
     * @return getAvailableStock result
     */
    public Integer getAvailableStock() {
        return totalStock - reservedStock;
    }
}
