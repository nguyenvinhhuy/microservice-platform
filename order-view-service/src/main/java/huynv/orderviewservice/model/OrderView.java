package huynv.orderviewservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Stores a denormalized order read model row for fast query APIs.
 */
@Entity
@Table(name = "order_view",
        indexes = {
                @Index(name = "idx_order_view_tenant", columnList = "tenant_id"),
                @Index(name = "idx_order_view_user", columnList = "tenant_id,user_id"),
                @Index(name = "idx_order_view_created_at", columnList = "created_at")
        })
public class OrderView {

    @EmbeddedId
    private OrderViewId id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "payment_status", length = 40)
    private String paymentStatus;

    @Column(name = "stock_status", length = 40)
    private String stockStatus;

    @Column(name = "total_price", precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Creates an empty entity instance for JPA.
     *
     * @return Initializes an OrderView instance.
     */
    public OrderView() {
    }

    public OrderViewId getId() {
        return id;
    }

    public void setId(OrderViewId id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getStockStatus() {
        return stockStatus;
    }

    public void setStockStatus(String stockStatus) {
        this.stockStatus = stockStatus;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Initializes audit timestamps before first persistence.
     *
     * @return Performs a side effect by initializing createdAt and updatedAt values.
     */
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    /**
     * Updates audit timestamp before entity updates.
     *
     * @return Performs a side effect by updating updatedAt.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

