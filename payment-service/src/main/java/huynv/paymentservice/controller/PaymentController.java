package huynv.paymentservice.controller;

import huynv.paymentservice.dto.PaymentProcessRequest;
import huynv.paymentservice.dto.PaymentResponse;
import huynv.paymentservice.exception.PaymentNotFoundException;
import huynv.paymentservice.repository.PaymentRepository;
import huynv.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes payment processing and query endpoints for the payment service.
 */
@RestController
@RequestMapping(path = "/api/payments", produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    /**
     * Creates a payment controller that delegates payment processing to the application service.
     *
     * @param paymentService Service used to process payments.
     * @param paymentRepository Repository used to load payment aggregates for query endpoints.
     * @return Initializes a payment controller.
     */
    public PaymentController(PaymentService paymentService, PaymentRepository paymentRepository) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Processes a payment request idempotently using the provided idempotency key.
     *
     * @param request Payment process request payload.
     * @return Returns the current payment state after processing or idempotent lookup.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PaymentResponse process(@Valid @RequestBody PaymentProcessRequest request) {
        return paymentService.processPayment(request);
    }

    /**
     * Returns a payment by payment identifier.
     *
     * @param paymentId Payment identifier to load.
     * @return Returns the current payment state for the requested payment.
     */
    @GetMapping("/{paymentId}")
    public PaymentResponse getById(@PathVariable UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(p -> new PaymentResponse(
                        p.getPaymentId(),
                        p.getOrderId(),
                        p.getStatus(),
                        p.getProvider(),
                        p.getTransactionId(),
                        p.getIdempotencyKey()
                ))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}

