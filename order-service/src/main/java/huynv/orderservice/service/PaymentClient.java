package huynv.orderservice.service;

import huynv.orderservice.config.PaymentClientProperties;
import huynv.orderservice.dto.PaymentProcessRequest;
import huynv.orderservice.dto.PaymentResponse;
import huynv.orderservice.exception.PaymentFailedException;
import huynv.orderservice.resilience.ResilienceExecutor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Calls payment-service synchronously for charge operations during order orchestration.
 */
@Component
public class PaymentClient {

    private final WebClient paymentWebClient;
    private final PaymentClientProperties properties;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Creates a payment client backed by WebClient.
     *
     * @param paymentWebClient WebClient configured for payment-service base URL and timeouts.
     * @param properties Payment client properties containing endpoint paths.
     * @param resilienceExecutor Resilience executor used to apply CircuitBreaker, Retry, Timeout, and Bulkhead.
     * @return Initializes a payment client instance.
     */
    public PaymentClient(WebClient paymentWebClient, PaymentClientProperties properties, ResilienceExecutor resilienceExecutor) {
        this.paymentWebClient = paymentWebClient;
        this.properties = properties;
        this.resilienceExecutor = resilienceExecutor;
    }

    /**
     * Charges payment for the given order using payment-service idempotency semantics.
     *
     * @param orderId Order identifier being charged.
     * @param tenantId Tenant scope for ownership validation.
     * @param amount Amount to charge for the order.
     * @param currency ISO currency code for the amount.
     * @param provider Provider identifier to route payment to a specific provider client.
     * @param idempotencyKey Idempotency key used to prevent double charges across retries.
     * @param correlationId Correlation identifier propagated across services for one business flow.
     * @return returns the payment response representing the persisted payment result.
     */
    public PaymentResponse charge(UUID orderId,
                                 Long tenantId,
                                 java.math.BigDecimal amount,
                                 String currency,
                                 String provider,
                                 String idempotencyKey,
                                 String correlationId) {
        String traceId = MDC.get("traceId");
        return resilienceExecutor.execute("paymentService", () -> {
            try {
                PaymentProcessRequest request = new PaymentProcessRequest(
                        orderId,
                        tenantId,
                        amount,
                        currency,
                        provider,
                        idempotencyKey,
                        correlationId,
                        traceId
                );
                return paymentWebClient.post()
                        .uri(properties.getProcessPath())
                        .header("X-Tenant-Id", String.valueOf(tenantId))
                        .header("X-User-Id", String.valueOf(0L))
                        .header("X-Roles", "ROLE_SYSTEM")
                        .header("X-Request-Id", idempotencyKey)
                        .header("X-Trace-Id", traceId != null ? traceId : UUID.randomUUID().toString())
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(PaymentResponse.class)
                        .block();
            } catch (WebClientResponseException ex) {
                throw new PaymentFailedException("Payment-service charge failed orderId=" + orderId + " status=" + ex.getStatusCode(), ex);
            } catch (Exception ex) {
                throw new PaymentFailedException("Payment-service charge failed orderId=" + orderId + ".", ex);
            }
        });
    }
}
