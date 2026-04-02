package huynv.inventoryservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Represents a reservation of stock for a specific order.
@Entity
@Table(
        name = "inventory_reservation",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reservation_tenant_reservation_id", columnNames = {"tenant_id", "reservation_id"}),
                @UniqueConstraint(name = "uk_reservation_tenant_order_id", columnNames = {"tenant_id", "order_id"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique identifier for the reservation.
    @Column(nullable = false)
    private UUID reservationId;

    // The ID of the order this reservation is for.
    @Column(nullable = false)
    private UUID orderId;

    // The tenant ID for multi-tenancy.
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // The status of the reservation.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    // The time at which this reservation expires.
    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "payment_provider", length = 64)
    private String paymentProvider;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    // The items included in this reservation.
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryReservationItem> items;
}
