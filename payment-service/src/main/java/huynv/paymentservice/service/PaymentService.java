package huynv.paymentservice.service;

import huynv.paymentservice.dto.PaymentProcessRequest;
import huynv.paymentservice.dto.PaymentResponse;
import huynv.paymentservice.domain.PaymentStatus;
import huynv.paymentservice.metrics.PaymentMetrics;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Provides a non-transactional orchestration layer that delegates all payment mutations to PaymentTransactionService.
 */
@Service
public class PaymentService {

    private final PaymentTransactionService paymentTransactionService;
    private final PaymentMetrics paymentMetrics;

    /**
     * Creates a payment service that records metrics and delegates transactional work to a separate bean.
     *
     * @param paymentTransactionService Transactional service responsible for payment state mutations and outbox writes.
     * @param paymentMetrics Metrics recorder for payment processing.
     * @return Initializes a payment service orchestration layer.
     */
    public PaymentService(PaymentTransactionService paymentTransactionService, PaymentMetrics paymentMetrics) {
        this.paymentTransactionService = paymentTransactionService;
        this.paymentMetrics = paymentMetrics;
    }

    /**
     * Processes a payment request originating from the REST API.
     *
     * @param request Request payload containing order and payment details.
     * @param idempotencyKey Canonical REST idempotency key supplied via HTTP header.
     * @return Returns the current payment state after processing or idempotent lookup.
     */
    public PaymentResponse processPayment(PaymentProcessRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        paymentMetrics.recordRequest();
        OffsetDateTime start = OffsetDateTime.now();
        try {
            PaymentResponse response = paymentTransactionService.processFromApi(request, idempotencyKey);
            if (response.status() == PaymentStatus.SUCCEEDED) {
                paymentMetrics.recordSuccess();
            } else if (response.status() == PaymentStatus.FAILED) {
                paymentMetrics.recordFailure();
            }
            return response;
        } finally {
            paymentMetrics.recordLatency(Duration.between(start, OffsetDateTime.now()));
        }
    }
}
