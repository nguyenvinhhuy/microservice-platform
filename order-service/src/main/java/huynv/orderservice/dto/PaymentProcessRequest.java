package huynv.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Defines the request payload sent from order-service to payment-service for synchronous charging.
 */
public record PaymentProcessRequest(
        UUID orderId,
        Long tenantId,
        BigDecimal amount,
        String currency,
        String paymentProvider,
        String idempotencyKey,
        String correlationId,
        String traceId
) {
}

