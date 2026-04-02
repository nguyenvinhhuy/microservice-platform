package huynv.inventoryservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Represents a single item within an inventory reservation.
@Entity
@Table(name = "inventory_reservation_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The reservation this item belongs to.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    // The ID of the product being reserved.
    @Column(nullable = false)
    private Long productId;

    // The quantity of the product being reserved.
    @Column(nullable = false)
    private Integer quantity;
}
