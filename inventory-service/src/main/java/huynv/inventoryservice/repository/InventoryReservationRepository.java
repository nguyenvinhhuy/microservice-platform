package huynv.inventoryservice.repository;

import huynv.inventoryservice.domain.InventoryReservation;
import huynv.inventoryservice.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for InventoryReservation entities.
 * Contains tenant-aware methods to ensure data security and isolation.
 */
@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    /**
     * Finds a reservation by its order ID and tenant ID.
     * This is the primary method for retrieving a reservation, ensuring a tenant
     * cannot access another tenant's reservation data.
     *
     * @param orderId  The unique order ID.
     * @param tenantId The ID of the tenant.
     * @return An Optional containing the reservation if found.
     */
    Optional<InventoryReservation> findByOrderIdAndTenantId(UUID orderId, Long tenantId);

    List<InventoryReservation> findByStatusAndExpiresAtBeforeAndTenantIdIsNotNull(ReservationStatus status, OffsetDateTime expiresAt);
}
