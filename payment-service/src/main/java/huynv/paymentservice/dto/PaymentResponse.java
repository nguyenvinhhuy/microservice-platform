package huynv.paymentservice.dto;

import huynv.paymentservice.domain.PaymentStatus;

import java.util.UUID;

/**
 * Defines the API response payload for a payment aggregate.
 */
public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status,
        String provider,
        String transactionId,
        String idempotencyKey
) {
}

