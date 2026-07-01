package huynv.paymentservice.service;

import huynv.event.BaseEvent;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.inventory.StockReservedEvent;
import huynv.paymentservice.config.PaymentProperties;
import huynv.paymentservice.domain.Payment;
import huynv.paymentservice.domain.PaymentStatus;
import huynv.paymentservice.dto.PaymentResponse;
import huynv.paymentservice.event.PaymentEventProducer;
import huynv.paymentservice.exception.NonRetryableMessageException;
import huynv.paymentservice.exception.PaymentProviderDeclinedException;
import huynv.paymentservice.repository.PaymentRepository;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentTransactionService — covers core payment processing paths,
 * idempotency, fraud decisions, and reconciliation jobs.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private PaymentEventProducer paymentEventProducer;
    @Mock
    private FraudCheckService fraudCheckService;
    @Mock
    private PaymentProviderClient paymentProviderClient;

    private final PaymentProperties paymentProperties = new PaymentProperties();

    // Single-attempt retry to avoid delays in unit tests.
    private final RetryRegistry retryRegistry = RetryRegistry.of(
            RetryConfig.custom().maxAttempts(1).build());

    private PaymentTransactionService service;

    /**
     * Constructs a {@link PaymentTransactionService} with a single-attempt retry registry so
     * tests complete without real backoff delays, and resets properties to their defaults before
     * each test.
     *
     * @return void — initialises the service under test with all required collaborators.
     */
    @BeforeEach
    void setUp() {
        service = new PaymentTransactionService(
                paymentProperties,
                paymentRepository,
                idempotencyService,
                paymentEventProducer,
                fraudCheckService,
                paymentProviderClient,
                retryRegistry);
    }

    // -----------------------------------------------------------------------
    // processPayment — happy path
    // -----------------------------------------------------------------------

    /**
     * Verifies that when the fraud check approves the transaction the payment provider is charged,
     * a SUCCEEDED status is returned, and both processing and succeeded outbox events are enqueued.
     *
     * @return void — asserts the full happy-path through fraud check, provider charge, and event
     *     emission.
     */
    @Test
    void processPayment_approvedFraud_chargesProviderAndReturnsSucceeded() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-1";

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudCheckService.check(eq(orderId), any(), any(), any())).thenReturn(FraudCheckDecision.APPROVE);
        when(paymentProviderClient.charge(eq(orderId), any(), any(), any())).thenReturn("tx-abc");

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, "corr-1", "trace-1");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.transactionId()).isEqualTo("tx-abc");
        assertThat(response.idempotencyKey()).isEqualTo(idemKey);
        verify(paymentEventProducer).enqueueProcessing(any(), any());
        verify(paymentEventProducer).enqueueSucceeded(any(), any());
    }

    /**
     * Verifies that when an idempotency key maps to an already-succeeded payment the service
     * returns the cached SUCCEEDED response without invoking the fraud check or provider charge.
     *
     * @return void — asserts that idempotent replay of a completed payment skips all processing
     *     steps.
     */
    @Test
    void processPayment_existingSucceededPayment_returnsWithoutRecharging() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-existing";

        Payment existing = pendingPayment(orderId, idemKey);
        existing.markProcessing(OffsetDateTime.now());
        existing.markSucceeded("tx-old", OffsetDateTime.now());
        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existing));

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, null, null);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentProviderClient, never()).charge(any(), any(), any(), any());
        verify(fraudCheckService, never()).check(any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // processPayment — kill switch
    // -----------------------------------------------------------------------

    /**
     * Verifies that when the processing kill switch is disabled the service returns a FAILED
     * status with the PROCESSING_DISABLED reason without calling the payment provider.
     *
     * @return void — asserts that the processing kill switch prevents provider interaction and
     *     records a failed event.
     */
    @Test
    void processPayment_processingDisabled_returnsFailedWithoutCharge() {
        paymentProperties.getProcessing().setEnabled(false);
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-disabled";

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, null, null);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentProviderClient, never()).charge(any(), any(), any(), any());
        verify(paymentEventProducer).enqueueFailed(any(), eq("PROCESSING_DISABLED"), any());
    }

    // -----------------------------------------------------------------------
    // processPayment — fraud decisions
    // -----------------------------------------------------------------------

    /**
     * Verifies that when the fraud check returns REJECT the payment is marked FAILED with the
     * FRAUD_REJECTED reason and the provider charge is never attempted.
     *
     * @return void — asserts that a fraud rejection produces the correct failed status and event
     *     reason without provider interaction.
     */
    @Test
    void processPayment_fraudRejected_returnsFailedWithRejectedReason() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-fraud-reject";

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudCheckService.check(any(), any(), any(), any())).thenReturn(FraudCheckDecision.REJECT);

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, null, null);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventProducer).enqueueFailed(any(), eq("FRAUD_REJECTED"), any());
        verify(paymentProviderClient, never()).charge(any(), any(), any(), any());
    }

    /**
     * Verifies that when the fraud check returns REVIEW the payment is marked FAILED with the
     * FRAUD_REVIEW reason, blocking the charge until manual review completes.
     *
     * @return void — asserts that a fraud review decision produces the correct failed status and
     *     event reason.
     */
    @Test
    void processPayment_fraudReview_returnsFailedWithReviewReason() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-fraud-review";

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudCheckService.check(any(), any(), any(), any())).thenReturn(FraudCheckDecision.REVIEW);

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, null, null);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventProducer).enqueueFailed(any(), eq("FRAUD_REVIEW"), any());
    }

    /**
     * Verifies that when the payment provider throws {@link PaymentProviderDeclinedException}
     * the service catches it, marks the payment FAILED with the PROVIDER_DECLINED reason, and
     * enqueues the failed event.
     *
     * @return void — asserts that a provider decline is correctly surfaced as a FAILED payment
     *     with the expected failure reason.
     */
    @Test
    void processPayment_providerDeclined_returnsFailedWithDeclinedReason() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-declined";

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudCheckService.check(any(), any(), any(), any())).thenReturn(FraudCheckDecision.APPROVE);
        when(paymentProviderClient.charge(any(), any(), any(), any()))
                .thenThrow(new PaymentProviderDeclinedException("Card declined"));

        PaymentResponse response = service.processPayment(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, null, null);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventProducer).enqueueFailed(any(), eq("PROVIDER_DECLINED"), any());
    }

    // -----------------------------------------------------------------------
    // processFromStockReserved
    // -----------------------------------------------------------------------

    /**
     * Verifies that when the idempotency service reports the event has already been processed the
     * service skips all payment logic and returns the existing persisted payment without charging
     * the provider again.
     *
     * @return void — asserts that event-level idempotency prevents duplicate provider charges on
     *     Kafka message replay.
     */
    @Test
    void processFromStockReserved_alreadyProcessed_skipsProcessingAndReturnsExistingPayment() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-saga-1";

        BaseEvent<StockReservedEvent> event = stockReservedEvent(orderId, idemKey);
        when(idempotencyService.alreadyProcessed(event.eventId())).thenReturn(true);

        Payment existing = pendingPayment(orderId, idemKey);
        existing.markProcessing(OffsetDateTime.now());
        existing.markSucceeded("tx-saga", OffsetDateTime.now());
        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existing));

        PaymentResponse response = service.processFromStockReserved(event);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentProviderClient, never()).charge(any(), any(), any(), any());
    }

    /**
     * Verifies that for a previously unseen stock-reserved event the service runs the full payment
     * flow and marks the event ID as processed in the idempotency store upon completion.
     *
     * @return void — asserts that a new stock-reserved event triggers payment processing and
     *     records the event ID to prevent reprocessing.
     */
    @Test
    void processFromStockReserved_newEvent_processesAndMarksEventIdempotent() {
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-saga-new";

        BaseEvent<StockReservedEvent> event = stockReservedEvent(orderId, idemKey);
        when(idempotencyService.alreadyProcessed(event.eventId())).thenReturn(false);

        when(paymentRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudCheckService.check(any(), any(), any(), any())).thenReturn(FraudCheckDecision.APPROVE);
        when(paymentProviderClient.charge(any(), any(), any(), any())).thenReturn("tx-saga-new");

        PaymentResponse response = service.processFromStockReserved(event);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(idempotencyService).markProcessed(event.eventId());
    }

    /**
     * Verifies that a stock-reserved event with a null eventId raises
     * {@link NonRetryableMessageException} containing "eventId" in the message, preventing the
     * consumer from retrying an inherently corrupt message.
     *
     * @return void — asserts that a missing event ID is treated as a non-retryable message
     *     processing error.
     */
    @Test
    void processFromStockReserved_missingEventId_throwsNonRetryable() {
        BaseEvent<StockReservedEvent> event = new BaseEvent<>(
                null, "type", "src", Instant.now(), "agg", 1L, "schema",
                null, null, null,
                new StockReservedEvent(UUID.randomUUID(), 1L, BigDecimal.TEN, "USD", "sim", "idem", List.of()));

        assertThatThrownBy(() -> service.processFromStockReserved(event))
                .isInstanceOf(NonRetryableMessageException.class)
                .hasMessageContaining("eventId");
    }

    /**
     * Verifies that a stock-reserved event with a null orderId in its payload raises
     * {@link NonRetryableMessageException} containing "orderId" in the message, preventing the
     * consumer from retrying a corrupt message.
     *
     * @return void — asserts that a missing order ID in the event payload is treated as a
     *     non-retryable error.
     */
    @Test
    void processFromStockReserved_missingOrderId_throwsNonRetryable() {
        BaseEvent<StockReservedEvent> event = new BaseEvent<>(
                "evt-1", "type", "src", Instant.now(), "agg", 1L, "schema",
                null, null, null,
                new StockReservedEvent(null, 1L, BigDecimal.TEN, "USD", "sim", "idem", List.of()));

        assertThatThrownBy(() -> service.processFromStockReserved(event))
                .isInstanceOf(NonRetryableMessageException.class)
                .hasMessageContaining("orderId");
    }

    // -----------------------------------------------------------------------
    // reconcileProcessingPayments
    // -----------------------------------------------------------------------

    /**
     * Verifies that when the provider reports a stuck-processing payment as SUCCEEDED the
     * reconciliation job updates the persisted payment to SUCCEEDED and enqueues a succeeded
     * outbox event.
     *
     * @return void — asserts that reconciliation correctly promotes a PROCESSING payment to
     *     SUCCEEDED when the provider confirms completion.
     */
    @Test
    void reconcileProcessingPayments_succeededFromProvider_marksPaymentSucceededAndEnqueuesEvent() {
        UUID orderId = UUID.randomUUID();
        Payment processing = processingPaymentWithTxId(orderId, "tx-rec-1");
        when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(processing));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProviderClient.getTransactionStatus("tx-rec-1"))
                .thenReturn(PaymentProviderTransactionStatus.SUCCEEDED);

        service.reconcileProcessingPayments(OffsetDateTime.now(), 10);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentEventProducer).enqueueSucceeded(any(), any());
    }

    /**
     * Verifies that when the provider reports a stuck-processing payment as FAILED the
     * reconciliation job updates the persisted payment to FAILED and enqueues a failed outbox
     * event with the RECONCILE_FAILED reason.
     *
     * @return void — asserts that reconciliation correctly marks a PROCESSING payment as FAILED
     *     when the provider reports failure.
     */
    @Test
    void reconcileProcessingPayments_failedFromProvider_marksPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        Payment processing = processingPaymentWithTxId(orderId, "tx-rec-fail");
        when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(processing));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProviderClient.getTransactionStatus("tx-rec-fail"))
                .thenReturn(PaymentProviderTransactionStatus.FAILED);

        service.reconcileProcessingPayments(OffsetDateTime.now(), 10);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventProducer).enqueueFailed(any(), eq("RECONCILE_FAILED"), any());
    }

    // -----------------------------------------------------------------------
    // failProcessingTimeouts
    // -----------------------------------------------------------------------

    /**
     * Verifies that the timeout job marks stuck-processing payments as FAILED with the provided
     * failure reason and enqueues a failed event for each timed-out payment.
     *
     * @return void — asserts that payments lingering in PROCESSING beyond the timeout threshold
     *     are transitioned to FAILED with the PROCESSING_TIMEOUT reason.
     */
    @Test
    void failProcessingTimeouts_stuckPayments_marksAllFailed() {
        UUID orderId = UUID.randomUUID();
        Payment payment = pendingPayment(orderId, "idem-timeout");
        payment.markProcessing(OffsetDateTime.now());
        when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.failProcessingTimeouts(OffsetDateTime.now(), 10, "PROCESSING_TIMEOUT");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventProducer).enqueueFailed(any(), eq("PROCESSING_TIMEOUT"), any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link Payment} in PENDING status for the given order ID and idempotency key,
     * using sensible defaults for tenant, amount, currency, provider, and trace context.
     *
     * @param orderId the order ID to associate with the payment.
     * @param idemKey the idempotency key that uniquely identifies this payment attempt.
     * @return a new {@link Payment} entity in PENDING status ready for use in tests.
     */
    private Payment pendingPayment(UUID orderId, String idemKey) {
        return Payment.createPending(
                UUID.randomUUID(),
                orderId,
                1L,
                BigDecimal.TEN,
                "USD",
                "simulated",
                idemKey,
                "corr",
                "trace",
                OffsetDateTime.now());
    }

    /** Creates a payment in PROCESSING state with a transactionId set via reflection (crash recovery scenario). */
    private Payment processingPaymentWithTxId(UUID orderId, String transactionId) {
        Payment p = pendingPayment(orderId, "idem-proc-" + transactionId);
        p.markProcessing(OffsetDateTime.now());
        ReflectionTestUtils.setField(p, "transactionId", transactionId);
        return p;
    }

    /**
     * Builds a {@link BaseEvent} wrapping a {@link StockReservedEvent} for the given order ID
     * and idempotency key, with trace and correlation metadata populated for realistic saga
     * consumer scenarios.
     *
     * @param orderId the order ID to embed in both the event envelope and the payload.
     * @param idemKey the idempotency key carried inside the stock-reserved payload.
     * @return a fully populated {@link BaseEvent} containing a {@link StockReservedEvent}.
     */
    private BaseEvent<StockReservedEvent> stockReservedEvent(UUID orderId, String idemKey) {
        StockReservedEvent data = new StockReservedEvent(
                orderId, 1L, BigDecimal.TEN, "USD", "simulated", idemKey, List.of());
        return new BaseEvent<>(
                "evt-" + UUID.randomUUID(),
                "inventory.stock.reserved",
                "inventory-service",
                Instant.now(),
                orderId.toString(),
                1L,
                "inventory.stock.reserved.v1",
                "trace-1",
                "corr-1",
                null,
                data);
    }
}
