package huynv.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Defines the API request payload for initiating payment processing.
 */
public record PaymentProcessRequest(
        @NotNull UUID orderId,
        @NotNull Long tenantId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String paymentProvider,
        @NotBlank String idempotencyKey,
        String correlationId,
        String traceId
) {
}
