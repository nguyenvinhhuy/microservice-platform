package huynv.productviewservice.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Defines the public response model for product view queries.
 */
public record ProductViewResponse(
        Long productId,
        String name,
        BigDecimal price,
        Integer stock,
        String status,
        OffsetDateTime updatedAt
) {
}

