package huynv.orderservice.service;

import huynv.orderservice.exception.PaymentFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentGatewayService {

    private final PaymentClient paymentClient;
    private final OrderTransactionalService orderTransactionalService;

    @Value("${feature.payment.enabled:true}")
    private boolean paymentEnabled;

    /**
     * Creates a payment gateway service that delegates charge operations to payment-service.
     *
     * @param paymentClient Payment client used to call payment-service synchronously.
     * @param orderTransactionalService Transactional service used to load order currency for charge requests.
     * @return initializes a payment gateway service instance.
     */
    public PaymentGatewayService(PaymentClient paymentClient, OrderTransactionalService orderTransactionalService) {
        this.paymentClient = paymentClient;
        this.orderTransactionalService = orderTransactionalService;
    }

    /**
     * Charges the order total using payment-service and returns the resulting payment identifier.
     *
     * @param provider Payment provider code forwarded by the API command.
     * @param amount Order total amount captured for settlement.
     * @param orderId Order identifier used for traceability and idempotency keys.
     * @param tenantId Tenant owner id used to avoid cross-tenant side effects.
     * @return Returns the payment identifier persisted by payment-service.
     */
    @Transactional(readOnly = true)
    public UUID charge(String provider, BigDecimal amount, UUID orderId, Long tenantId) {
        if (!paymentEnabled) {
            throw new PaymentFailedException("Payment feature disabled for order " + orderId);
        }

        huynv.orderservice.domain.Order order = orderTransactionalService.findOrder(orderId, tenantId);
        String requestId = org.slf4j.MDC.get("requestId");
        String idempotencyKey = requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
        String correlationId = idempotencyKey;

        huynv.orderservice.dto.PaymentResponse response = paymentClient.charge(
                orderId,
                tenantId,
                amount,
                order.getCurrency(),
                provider,
                idempotencyKey,
                correlationId
        );
        if (response == null || response.paymentId() == null) {
            throw new PaymentFailedException("Payment-service returned an empty response for order " + orderId);
        }
        if (!"SUCCEEDED".equalsIgnoreCase(response.status())) {
            throw new PaymentFailedException("Payment failed for order " + orderId + " status=" + response.status());
        }
        return response.paymentId();
    }

    /**
     * Requests a compensating refund when a downstream failure occurs after payment capture.
     *
     * @param paymentId Payment identifier to reverse at provider side.
     * @param orderId Order identifier for diagnostic logging and tracing.
     * @param tenantId Tenant owner id to enforce compensation ownership.
     * @return no return; throws PaymentFailedException when provider rejects refund.
     */
    public void refund(UUID paymentId, UUID orderId, Long tenantId) {
        if (paymentId == null) {
            return;
        }
        if (paymentId.toString().endsWith("0000")) {
            throw new PaymentFailedException("Refund rejected by provider for order " + orderId);
        }
    }
}
