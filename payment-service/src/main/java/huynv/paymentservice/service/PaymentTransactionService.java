package huynv.paymentservice.service;

import huynv.paymentservice.domain.Payment;
import huynv.paymentservice.domain.PaymentStatus;
import huynv.event.BaseEvent;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.inventory.StockReservedEvent;
import huynv.paymentservice.dto.PaymentProcessRequest;
import huynv.paymentservice.dto.PaymentResponse;
import huynv.paymentservice.event.PaymentEventProducer;
import huynv.paymentservice.exception.NonRetryableMessageException;
import huynv.paymentservice.exception.PaymentOptimisticLockException;
import huynv.paymentservice.exception.PaymentProviderDeclinedException;
import huynv.paymentservice.exception.PaymentProviderTimeoutException;
import huynv.paymentservice.repository.PaymentRepository;
import huynv.paymentservice.util.TraceContextUtil;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import huynv.paymentservice.config.PaymentProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/**
 * Executes payment state mutations, outbox writes, and processed event markers within a single transaction.
 */
@Service
public class PaymentTransactionService {

    private final PaymentProperties paymentProperties;
    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventProducer paymentEventProducer;
    private final FraudCheckService fraudCheckService;
    private final PaymentProviderClient paymentProviderClient;
    private final RetryRegistry retryRegistry;

    /**
     * Creates a transactional payment service used to guarantee atomic updates for payment processing.
     *
     * @param paymentRepository Repository used to persist payment aggregates.
     * @param idempotencyService Service used to enforce consumer idempotency for inbound Kafka events.
     * @param paymentEventProducer Producer used to enqueue payment events into the outbox.
     * @param fraudCheckService Fraud check hook invoked before charging the provider.
     * @param paymentProviderClient Provider client used to charge payments.
     * @param retryRegistry Resilience4j registry used to configure provider retries.
     * @return Initializes a transactional payment service.
     */
    public PaymentTransactionService(
            PaymentProperties paymentProperties,
            PaymentRepository paymentRepository,
            IdempotencyService idempotencyService,
            PaymentEventProducer paymentEventProducer,
            FraudCheckService fraudCheckService,
            PaymentProviderClient paymentProviderClient,
            RetryRegistry retryRegistry
    ) {
        this.paymentProperties = paymentProperties;
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.paymentEventProducer = paymentEventProducer;
        this.fraudCheckService = fraudCheckService;
        this.paymentProviderClient = paymentProviderClient;
        this.retryRegistry = retryRegistry;
    }

    /**
     * Processes a payment request originating from the REST API with idempotency and atomic outbox writes.
     *
     * @param request Request payload containing order and payment details.
     * @param idempotencyKey Canonical REST idempotency key supplied via HTTP header.
     * @return Returns the current payment state after processing or idempotent lookup.
     */
    @Transactional
    public PaymentResponse processFromApi(PaymentProcessRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return processPayment(
                request.orderId(),
                request.tenantId(),
                request.amount(),
                request.currency(),
                request.paymentProvider(),
                idempotencyKey,
                request.correlationId(),
                request.traceId()
        );
    }

    /**
     * Processes a stock reserved saga event exactly once and records the processed marker atomically.
     *
     * @param event Unified stock reserved event envelope.
     * @return Returns the current payment state after processing or idempotent lookup.
     */
    @Transactional
    public PaymentResponse processFromStockReserved(BaseEvent<StockReservedEvent> event) {
        Objects.requireNonNull(event, "event");
        validateStockReservedEvent(event);

        if (idempotencyService.alreadyProcessed(event.eventId())) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(event.data().idempotencyKey());
            return existing.map(PaymentTransactionService::toResponse).orElseGet(() -> new PaymentResponse(
                    null,
                    event.data().orderId(),
                    PaymentStatus.CANCELLED,
                    event.data().paymentProvider(),
                    null,
                    event.data().idempotencyKey()
            ));
        }

        PaymentResponse response = processPayment(
                event.data().orderId(),
                event.data().tenantId(),
                event.data().amount(),
                event.data().currency(),
                event.data().paymentProvider(),
                event.data().idempotencyKey(),
                event.correlationId(),
                event.traceId()
        );

        idempotencyService.markProcessed(event.eventId());
        return response;
    }

    /**
     * Core payment processing logic used by both REST and saga flows.
     *
     * @param orderId Order identifier associated with the payment.
     * @param tenantId Tenant identifier for multi-tenant correlation when available.
     * @param amount Amount to charge.
     * @param currency ISO currency code.
     * @param provider Provider identifier.
     * @param idempotencyKey Idempotency key used to prevent duplicate charges.
     * @param correlationId Correlation identifier used to trace the saga across services.
     * @param traceId Trace identifier used to correlate distributed traces.
     * @return Returns the current payment state after processing or idempotent lookup.
     */
    @Transactional
    public PaymentResponse processPayment(
            UUID orderId,
            Long tenantId,
            BigDecimal amount,
            String currency,
            String provider,
            String idempotencyKey,
            String correlationId,
            String traceId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = getOrCreatePending(orderId, tenantId, amount, currency, provider, idempotencyKey, correlationId, traceId, now);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELLED) {
            return toResponse(payment);
        }

        if (!paymentProperties.getProcessing().isEnabled()) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.markProcessing(now);
            }
            payment.markFailed(OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueFailed(payment, "PROCESSING_DISABLED", OffsetDateTime.now());
            return toResponse(payment);
        }

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markProcessing(now);
            paymentRepository.save(payment);
            paymentEventProducer.enqueueProcessing(payment, now);
        }

        FraudCheckDecision decision = fraudCheckService.check(orderId, tenantId, amount, currency);
        if (decision == FraudCheckDecision.REJECT) {
            payment.markFailed(OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueFailed(payment, "FRAUD_REJECTED", OffsetDateTime.now());
            return toResponse(payment);
        }
        if (decision == FraudCheckDecision.REVIEW) {
            payment.markFailed(OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueFailed(payment, "FRAUD_REVIEW", OffsetDateTime.now());
            return toResponse(payment);
        }

        try {
            String transactionId = chargeWithRetry(orderId, amount, currency, idempotencyKey);
            payment.markSucceeded(transactionId, OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueSucceeded(payment, OffsetDateTime.now());
            return toResponse(payment);
        } catch (PaymentProviderTimeoutException timeout) {
            throw timeout;
        } catch (PaymentProviderDeclinedException declined) {
            payment.markFailed(OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueFailed(payment, "PROVIDER_DECLINED", OffsetDateTime.now());
            return toResponse(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PaymentOptimisticLockException("Optimistic lock conflict while processing payment for orderId=" + orderId + ".");
        }
    }

    /**
     * Reconciles long-running PROCESSING payments by querying the provider and fixing mismatched states.
     *
     * @param cutoff Payments updated before this timestamp are considered for reconciliation.
     * @param batchSize Maximum number of payments reconciled per run.
     * @return Performs payment state updates and outbox writes for reconciled payments.
     */
    @Transactional
    public void reconcileProcessingPayments(OffsetDateTime cutoff, int batchSize) {
        for (Payment payment : paymentRepository.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff, PageRequest.of(0, batchSize))) {
            if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
                continue;
            }
            PaymentProviderTransactionStatus status = paymentProviderClient.getTransactionStatus(payment.getTransactionId());
            if (status == PaymentProviderTransactionStatus.SUCCEEDED) {
                payment.markSucceeded(payment.getTransactionId(), OffsetDateTime.now());
                paymentRepository.save(payment);
                paymentEventProducer.enqueueSucceeded(payment, OffsetDateTime.now());
            } else if (status == PaymentProviderTransactionStatus.FAILED) {
                payment.markFailed(OffsetDateTime.now());
                paymentRepository.save(payment);
                paymentEventProducer.enqueueFailed(payment, "RECONCILE_FAILED", OffsetDateTime.now());
            }
        }
    }

    /**
     * Marks payments stuck in PROCESSING beyond a timeout as FAILED and emits a failure event.
     *
     * @param cutoff Payments updated before this timestamp are considered timed out.
     * @param batchSize Maximum number of payments handled per run.
     * @param reason Failure reason used for the emitted failure event.
     * @return Performs payment state updates and outbox writes for timed out payments.
     */
    @Transactional
    public void failProcessingTimeouts(OffsetDateTime cutoff, int batchSize, String reason) {
        for (Payment payment : paymentRepository.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff, PageRequest.of(0, batchSize))) {
            payment.markFailed(OffsetDateTime.now());
            paymentRepository.save(payment);
            paymentEventProducer.enqueueFailed(payment, reason, OffsetDateTime.now());
        }
    }

    private Payment getOrCreatePending(
            UUID orderId,
            Long tenantId,
            BigDecimal amount,
            String currency,
            String provider,
            String idempotencyKey,
            String correlationId,
            String traceId,
            OffsetDateTime now
    ) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        String finalTraceId = traceId;
        String spanId = null;
        if (finalTraceId == null || finalTraceId.isBlank()) {
            TraceContextUtil.TraceIds ids = TraceContextUtil.currentTraceIdsOrNull();
            if (ids != null) {
                finalTraceId = ids.traceId();
                spanId = ids.spanId();
            }
        }

        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                orderId,
                tenantId,
                amount,
                currency,
                provider,
                idempotencyKey,
                correlationId,
                finalTraceId,
                now
        );
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency key conflict but payment was not found for idempotencyKey=" + idempotencyKey + ".", e));
        }
    }

    private String chargeWithRetry(UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        Retry retry = retryRegistry.retry("paymentProvider");
        return Retry.decorateSupplier(retry, () -> paymentProviderClient.charge(orderId, amount, currency, idempotencyKey)).get();
    }

    private static void validateStockReservedEvent(BaseEvent<StockReservedEvent> event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new NonRetryableMessageException("BaseEvent.eventId is required.");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new NonRetryableMessageException("BaseEvent.eventType is required.");
        }
        if (event.dataSchema() == null || event.dataSchema().isBlank()) {
            throw new NonRetryableMessageException("BaseEvent.dataSchema is required.");
        }
        if (event.data() == null) {
            throw new NonRetryableMessageException("BaseEvent.data is required.");
        }
        if (event.data().orderId() == null) {
            throw new NonRetryableMessageException("StockReservedData.orderId is required.");
        }
        if (event.data().tenantId() == null) {
            throw new NonRetryableMessageException("StockReservedData.tenantId is required.");
        }
        if (event.data().amount() == null) {
            throw new NonRetryableMessageException("StockReservedData.amount is required.");
        }
        if (event.data().currency() == null || event.data().currency().isBlank()) {
            throw new NonRetryableMessageException("StockReservedData.currency is required.");
        }
        if (event.data().paymentProvider() == null || event.data().paymentProvider().isBlank()) {
            throw new NonRetryableMessageException("StockReservedData.paymentProvider is required.");
        }
        if (event.data().idempotencyKey() == null || event.data().idempotencyKey().isBlank()) {
            throw new NonRetryableMessageException("StockReservedData.idempotencyKey is required.");
        }
    }

    private static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getTransactionId(),
                payment.getIdempotencyKey()
        );
    }
}


