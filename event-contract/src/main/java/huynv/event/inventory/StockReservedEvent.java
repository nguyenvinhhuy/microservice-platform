package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Defines the payload for the inventory.stock.reserved event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockReservedEvent(
        UUID orderId,
        Long tenantId,
        BigDecimal amount,
        String currency,
        String paymentProvider,
        String idempotencyKey,
        List<ReservedItem> items
) {
    /**
     * Defines one reserved item included in the reservation.
     *
     * @param productId Product identifier reserved.
     * @param quantity Quantity reserved.
     * @return Returns an immutable reserved item payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservedItem(Long productId, Integer quantity) {
    }
}

