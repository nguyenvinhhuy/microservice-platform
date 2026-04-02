package huynv.orderservice.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persisted saga aggregate used for crash-safe orchestration replay.
 * Contract:
 * - Delivery semantics per step are AT_LEAST_ONCE.
 * - Reversibility is represented by state COMPENSATING and persisted payment/reference fields.
 * - Crash recovery is driven by persisted state, retry counter, and last error fields.
 */
@Entity
@Table(name = "order_sagas",
        indexes = {
                @Index(name = "idx_order_saga_state_updated", columnList = "state,updated_at"),
                @Index(name = "idx_order_saga_tenant_order", columnList = "tenant_id,order_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_saga_tenant_order", columnNames = {"tenant_id", "order_id"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private OrderSagaState state;

    @Column(name = "payment_provider", length = 80)
    private String paymentProvider;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "request_id", length = 120)
    private String requestId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    /**
     * Initializes saga counters and timestamps for crash-safe step resumption.
     *
     * @param none lifecycle callback without explicit arguments
     * @return persists required defaults for deterministic step execution
     */
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Maintains update timestamp for saga diagnostics and scheduler ordering.
     *
     * @param none lifecycle callback without explicit arguments
     * @return records last change time used by resume workers
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
