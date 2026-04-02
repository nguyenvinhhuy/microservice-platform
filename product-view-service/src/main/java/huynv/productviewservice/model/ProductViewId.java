package huynv.productviewservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Defines the composite primary key for the product_view table using tenant and product identifiers.
 */
@Embeddable
public class ProductViewId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * Creates an empty id instance for JPA.
     *
     * @return Initializes an empty ProductViewId.
     */
    public ProductViewId() {
    }

    /**
     * Creates a composite id for a product view row.
     *
     * @param tenantId Tenant identifier used for multi-tenant isolation.
     * @param productId Product identifier within the tenant.
     * @return Initializes a ProductViewId instance.
     */
    public ProductViewId(Long tenantId, Long productId) {
        this.tenantId = tenantId;
        this.productId = productId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getProductId() {
        return productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProductViewId that = (ProductViewId) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, productId);
    }
}

