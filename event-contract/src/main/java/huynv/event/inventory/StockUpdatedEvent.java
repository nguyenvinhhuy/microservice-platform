package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Defines the payload for the inventory.stock.updated event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockUpdatedEvent(
        Long tenantId,
        Long productId,
        Integer totalStock,
        Integer reservedStock,
        Integer availableStock
) {
}

