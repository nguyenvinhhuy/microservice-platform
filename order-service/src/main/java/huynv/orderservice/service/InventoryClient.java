package huynv.orderservice.service;

import huynv.orderservice.config.InventoryClientProperties;
import huynv.orderservice.dto.InventoryReserveRequest;
import huynv.orderservice.exception.InventoryReservationFailedException;
import huynv.orderservice.resilience.ResilienceExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final WebClient inventoryWebClient;
    private final InventoryClientProperties properties;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Reserves stock in inventory-service for a given order using resilience protections.
     *
     * @param orderId Order identifier used for diagnostics and routing.
     * @param tenantId Tenant scope used for inventory ownership isolation.
     * @param request Reservation request containing product items and quantities.
     * @return Reserves inventory stock and throws InventoryReservationFailedException on failure.
     */
    public void reserveStock(UUID orderId, Long tenantId, InventoryReserveRequest request) {
        resilienceExecutor.execute("inventoryService", () -> {
            try {
                inventoryWebClient.post()
                        .uri(properties.getReservePath())
                        .headers(headers -> headers.set("X-Tenant-Id", String.valueOf(tenantId)))
                        .bodyValue(request)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                log.info("Inventory reserved for orderId={}", orderId);
                return null;
            } catch (WebClientResponseException ex) {
                throw new InventoryReservationFailedException("Failed to reserve inventory for order " + orderId + ", status=" + ex.getStatusCode(), ex);
            }
        });
    }

    /**
     * Confirms a prior inventory reservation for a given order using resilience protections.
     *
     * @param orderId Order identifier used for routing.
     * @param tenantId Tenant scope used for inventory ownership isolation.
     * @return Confirms inventory reservation and throws InventoryReservationFailedException on failure.
     */
    public void confirmStock(UUID orderId, Long tenantId) {
        resilienceExecutor.execute("inventoryService", () -> {
            try {
                inventoryWebClient.post()
                        .uri(properties.getConfirmPath(), Map.of("orderId", orderId))
                        .headers(headers -> headers.set("X-Tenant-Id", String.valueOf(tenantId)))
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                log.info("Inventory confirmed for orderId={}", orderId);
                return null;
            } catch (WebClientResponseException ex) {
                throw new InventoryReservationFailedException("Failed to confirm inventory for order " + orderId + ", status=" + ex.getStatusCode(), ex);
            }
        });
    }

    /**
     * Releases a prior inventory reservation for a given order using resilience protections.
     *
     * @param orderId Order identifier used for routing.
     * @param tenantId Tenant scope used for inventory ownership isolation.
     * @return Releases inventory reservation and throws InventoryReservationFailedException on failure.
     */
    public void releaseStock(UUID orderId, Long tenantId) {
        resilienceExecutor.execute("inventoryService", () -> {
            try {
                inventoryWebClient.post()
                        .uri(properties.getReleasePath(), Map.of("orderId", orderId))
                        .headers(headers -> headers.set("X-Tenant-Id", String.valueOf(tenantId)))
                        .retrieve()
                        .toBodilessEntity()
                        .block();
                log.info("Inventory released for orderId={}", orderId);
                return null;
            } catch (WebClientResponseException ex) {
                throw new InventoryReservationFailedException("Failed to release inventory for order " + orderId + ", status=" + ex.getStatusCode(), ex);
            }
        });
    }
}
