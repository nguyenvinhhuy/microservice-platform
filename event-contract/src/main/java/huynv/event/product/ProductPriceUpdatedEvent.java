package huynv.event.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Defines the payload for the product.price.updated event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductPriceUpdatedEvent(
        Long tenantId,
        Long productId,
        BigDecimal price,
        String currency
) {
}

