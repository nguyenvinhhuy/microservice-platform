package huynv.orderservice.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_tenant", columnList = "tenant_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_tenant_id", columnNames = {"tenant_id", "id"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "reservation_reference", length = 128)
    private String reservationReference;

    @Column(name = "payment_attempt_count", nullable = false)
    private Integer paymentAttemptCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private OrderPayment orderPayment;

    @Version
    private Long version;

    @PrePersist
    /**
     * prePersist operation.
     *
     * @return performs side effects defined by this operation
     */
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.paymentAttemptCount == null) {
            this.paymentAttemptCount = 0;
        }
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    @PreUpdate
    /**
     * preUpdate operation.
     *
     * @return performs side effects defined by this operation
     */
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * markReservationSucceeded operation.
     *
     * @param reservationReference input parameter
     * @return performs side effects defined by this operation
     */
    public void markReservationSucceeded(String reservationReference) {
        enforceTransition(OrderStatus.CREATED, OrderStatus.RESERVED);
        this.reservationReference = reservationReference;
        this.failureReason = null;
    }

    /**
     * markReservationFailed operation.
     *
     * @param reason input parameter
     * @return performs side effects defined by this operation
     */
    public void markReservationFailed(String reason) {
        enforceTransition(OrderStatus.CREATED, OrderStatus.FAILED);
        this.failureReason = reason;
    }

    /**
     * beginPaymentAttempt operation.
     *
     * @return performs side effects defined by this operation
     */
    public void beginPaymentAttempt() {
        enforceTransition(OrderStatus.RESERVED, OrderStatus.PAYMENT_IN_PROGRESS);
        this.paymentAttemptCount = this.paymentAttemptCount + 1;
    }

    /**
     * markPaid operation.
     *
     * @return performs side effects defined by this operation
     */
    public void markPaid() {
        enforceTransition(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.CONFIRMED);
        this.failureReason = null;
    }

    /**
     * markPaymentFailed operation.
     *
     * @param reason input parameter
     * @return performs side effects defined by this operation
     */
    public void markPaymentFailed(String reason) {
        enforceTransition(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.FAILED);
        this.failureReason = reason;
    }

    /**
     * markCancelled operation.
     *
     * @return performs side effects defined by this operation
     */
    public void markCancelled() {
        if (this.status == OrderStatus.CANCELLED) {
            return;
        }
        if (this.status == OrderStatus.CONFIRMED) {
            throw new DomainInvariantViolationException("Cannot cancel CONFIRMED order " + this.id);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * markCompensating operation.
     *
     * @param reason input parameter
     * @return performs side effects defined by this operation
     */
    public void markCompensating(String reason) {
        if (this.status != OrderStatus.RESERVED && this.status != OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new DomainInvariantViolationException("Cannot mark COMPENSATING for order " + this.id + " in status " + this.status);
        }
        this.status = OrderStatus.COMPENSATING;
        this.failureReason = reason;
    }

    /**
     * enforceTransition operation.
     *
     * @param current input parameter
     * @param target input parameter
     * @return performs side effects defined by this operation
     */
    private void enforceTransition(OrderStatus current, OrderStatus target) {
        if (this.status != current) {
            throw new DomainInvariantViolationException(
                    "Invalid transition for order " + this.id + ": " + this.status + " -> " + target
            );
        }
        this.status = target;
    }
}
