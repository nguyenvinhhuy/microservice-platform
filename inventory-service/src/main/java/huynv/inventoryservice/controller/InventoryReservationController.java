package huynv.inventoryservice.controller;

import huynv.inventoryservice.dto.ReserveStockRequest;
import huynv.inventoryservice.service.InventoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes internal inventory reservation endpoints used by order-service orchestration.
 */
@RestController
@RequestMapping("/internal/inventory/reservations")
public class InventoryReservationController {

    private final InventoryService inventoryService;

    @Value("${feature.inventory.reservation.enabled:true}")
    private boolean reservationFeatureEnabled;

    /**
     * Creates an internal controller for inventory reservation orchestration endpoints.
     *
     * @param inventoryService inventory service used to execute reservation operations.
     * @return initializes a reservation controller instance.
     */
    public InventoryReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Creates a stock reservation for the given order in an idempotent manner.
     *
     * @param request reservation request containing order id and item quantities.
     * @return Returns 204 when the reservation is created or already exists.
     */
    @PostMapping
    public ResponseEntity<Void> reserveStock(@RequestBody ReserveStockRequest request) {
        ensureReservationFeatureEnabled("reserveStock");
        inventoryService.reserveStock(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirms a previously created reservation after payment has succeeded.
     *
     * @param orderId order identifier owning the reservation.
     * @return Returns 204 when the reservation is confirmed or already confirmed.
     */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Void> confirmStock(@PathVariable UUID orderId) {
        ensureReservationFeatureEnabled("confirmStock");
        inventoryService.confirmStock(orderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Releases a previously created reservation after payment failure or order cancellation.
     *
     * @param orderId order identifier owning the reservation.
     * @return Returns 204 when the reservation is released or already released.
     */
    @PostMapping("/{orderId}/release")
    public ResponseEntity<Void> releaseStock(@PathVariable UUID orderId) {
        ensureReservationFeatureEnabled("releaseStock");
        inventoryService.releaseStock(orderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enforces kill switch semantics for inventory reservation operations.
     *
     * @param operation operation name used for explicit failure messages.
     * @return no return; throws IllegalStateException when reservation feature is disabled.
     */
    private void ensureReservationFeatureEnabled(String operation) {
        if (!reservationFeatureEnabled) {
            throw new IllegalStateException("Inventory reservation feature disabled operation=" + operation + ".");
        }
    }
}
