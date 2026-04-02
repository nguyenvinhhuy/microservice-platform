package huynv.orderviewservice.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Defines the public response model for order view queries.
 */
public record OrderViewResponse(
        UUID orderId,
        Long userId,
        String status,
        String paymentStatus,
        String stockStatus,
        BigDecimal totalPrice,
        OffsetDateTime createdAt
) {
}

