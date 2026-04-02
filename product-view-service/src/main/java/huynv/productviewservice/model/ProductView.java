package huynv.productviewservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Stores a denormalized product read model row for fast query APIs.
 */
@Entity
@Table(name = "product_view",
        indexes = {
                @Index(name = "idx_product_view_tenant", columnList = "tenant_id"),
                @Index(name = "idx_product_view_updated_at", columnList = "updated_at")
        })
public class ProductView {

    @EmbeddedId
    private ProductViewId id;

    @Column(name = "name", length = 300)
    private String name;

    @Column(name = "price", precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Creates an empty entity instance for JPA.
     *
     * @return Initializes a ProductView instance.
     */
    public ProductView() {
    }

    public ProductViewId getId() {
        return id;
    }

    public void setId(ProductViewId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Initializes the updated timestamp before first persistence.
     *
     * @return Performs a side effect by setting updatedAt when missing.
     */
    @PrePersist
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now();
        }
    }

    /**
     * Updates the audit timestamp before entity updates.
     *
     * @return Performs a side effect by updating updatedAt.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
