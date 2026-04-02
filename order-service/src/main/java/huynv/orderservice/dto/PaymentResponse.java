package huynv.orderservice.dto;

import java.util.UUID;

/**
 * Defines the payment-service response payload returned to order-service.
 */
public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        String status,
        String provider,
        String transactionId,
        String idempotencyKey
) {
}

