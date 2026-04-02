package huynv.orderviewservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Defines the composite primary key for the order_view table using tenant and order identifiers.
 */
@Embeddable
public class OrderViewId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * Creates an empty id instance for JPA.
     *
     * @return Initializes an empty OrderViewId.
     */
    public OrderViewId() {
    }

    /**
     * Creates a composite id for an order view row.
     *
     * @param tenantId Tenant identifier used for multi-tenant isolation.
     * @param orderId Order identifier within the tenant.
     * @return Initializes an OrderViewId instance.
     */
    public OrderViewId(Long tenantId, UUID orderId) {
        this.tenantId = tenantId;
        this.orderId = orderId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OrderViewId that = (OrderViewId) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, orderId);
    }
}

