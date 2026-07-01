package huynv.orderservice.saga;

import huynv.orderservice.domain.IdempotencyKey;
import huynv.orderservice.domain.IdempotencyStatus;
import huynv.orderservice.domain.Order;
import huynv.orderservice.domain.OrderItem;
import huynv.orderservice.domain.OrderStatus;
import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.OrderItemRequest;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.exception.InventoryReservationFailedException;
import huynv.orderservice.exception.PaymentFailedException;
import huynv.orderservice.repository.OrderSagaRepository;
import huynv.orderservice.service.IdempotencyService;
import huynv.orderservice.service.InventoryClient;
import huynv.orderservice.service.OrderTransactionalService;
import huynv.orderservice.service.PaymentGatewayService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderSagaCoordinator — verifies saga state transitions, idempotency contract,
 * compensation triggering, and resume behavior.
 * Uses SimpleMeterRegistry to avoid mocking Micrometer internals.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaCoordinatorTest {

    @Mock
    private OrderTransactionalService orderTransactionalService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private OrderSagaRepository orderSagaRepository;
    @Mock
    private ObjectProvider<OrderSagaCoordinator> selfProvider;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OrderSagaCoordinator coordinator;

    /**
     * Initialises the OrderSagaCoordinator with all mocked collaborators and reflectively sets
     * sagaEnabled to true and defaultPaymentProvider to "simulated" before each test.
     *
     * @return Configures the coordinator and wires the self-provider lenient stub.
     */
    @BeforeEach
    void setUp() {
        coordinator = new OrderSagaCoordinator(
                orderTransactionalService,
                idempotencyService,
                inventoryClient,
                paymentGatewayService,
                orderSagaRepository,
                meterRegistry,
                selfProvider);
        ReflectionTestUtils.setField(coordinator, "sagaEnabled", true);
        ReflectionTestUtils.setField(coordinator, "defaultPaymentProvider", "simulated");
        lenient().when(selfProvider.getObject()).thenReturn(coordinator);
    }

    // -----------------------------------------------------------------------
    // createOrder
    // -----------------------------------------------------------------------

    /**
     * Verifies the happy-path createOrder flow: the coordinator creates a pending order, saves a saga,
     * calls the inventory client to reserve stock, and returns a response with RESERVED status.
     *
     * @return Asserts response orderId and status, and verifies inventoryClient and idempotencyService interactions.
     */
    @Test
    void createOrder_happyPath_reservesStockAndReturnsReservedStatus() {
        Long tenantId = 1L;
        String idemKey = "idem-create-1";
        UUID orderId = UUID.randomUUID();

        IdempotencyKey iKey = idemKey(1L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "CREATE_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        Order created = order(orderId, tenantId, OrderStatus.CREATED);
        when(orderTransactionalService.createPendingOrder(eq(tenantId), eq(100L), any(), any(), any()))
                .thenReturn(created);

        OrderSaga saga = saga(10L, tenantId, orderId, OrderSagaState.RESERVE_STOCK);
        when(orderSagaRepository.save(any(OrderSaga.class))).thenReturn(saga);
        when(orderSagaRepository.findById(10L)).thenReturn(Optional.of(saga));

        Order reserved = order(orderId, tenantId, OrderStatus.RESERVED);
        when(orderTransactionalService.markReservationSucceeded(
                eq(orderId), eq(tenantId), any(), any(), any(), any()))
                .thenReturn(reserved);
        when(orderTransactionalService.findOrder(orderId, tenantId)).thenReturn(reserved);

        CreateOrderResponse response = coordinator.createOrder(tenantId, 100L, createRequest("USD"), idemKey);

        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.RESERVED.name());
        verify(inventoryClient).reserveStock(eq(orderId), eq(tenantId), any());
        verify(idempotencyService).complete(eq(1L), eq(orderId), any());
    }

    /**
     * Verifies that a duplicate createOrder call with a COMPLETED idempotency key returns
     * the stored response immediately without creating a new order or calling downstream services.
     *
     * @return Asserts response is the stored instance and verifies createPendingOrder is never called.
     */
    @Test
    void createOrder_completedIdempotencyKey_replayStoredResponse() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-create-replay";

        IdempotencyKey iKey = idemKey(1L, IdempotencyStatus.COMPLETED);
        when(idempotencyService.begin(tenantId, idemKey, "CREATE_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, false));

        CreateOrderResponse stored = CreateOrderResponse.builder()
                .orderId(orderId).status("RESERVED").build();
        when(idempotencyService.replay(iKey, CreateOrderResponse.class)).thenReturn(stored);

        CreateOrderResponse response = coordinator.createOrder(tenantId, 100L, createRequest("USD"), idemKey);

        assertThat(response).isSameAs(stored);
        verify(orderTransactionalService, never()).createPendingOrder(any(), any(), any(), any(), any());
    }

    /**
     * Verifies that a concurrent createOrder request with a PROCESSING idempotency key that was not
     * newly created returns a response with status PROCESSING and the bound orderId, without
     * executing order creation logic.
     *
     * @return Asserts response status is "PROCESSING" and orderId matches the existing key.
     */
    @Test
    void createOrder_processingKeyNotCreated_returnsStatusProcessing() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-create-inflight";

        IdempotencyKey iKey = idemKey(1L, IdempotencyStatus.PROCESSING);
        iKey.setOrderId(orderId);
        when(idempotencyService.begin(tenantId, idemKey, "CREATE_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, false));

        CreateOrderResponse response = coordinator.createOrder(tenantId, 100L, createRequest("USD"), idemKey);

        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getOrderId()).isEqualTo(orderId);
        verify(orderTransactionalService, never()).createPendingOrder(any(), any(), any(), any(), any());
    }

    /**
     * Verifies that an inventory reservation failure during createOrder marks the idempotency key
     * as FAILED, marks the order reservation as failed, and re-throws the exception to the caller.
     *
     * @return Asserts InventoryReservationFailedException is thrown and verifies idempotencyService.fail is called.
     */
    @Test
    void createOrder_inventoryFailure_marksIdempotencyFailedAndThrows() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-create-fail";

        IdempotencyKey iKey = idemKey(1L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "CREATE_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        Order created = order(orderId, tenantId, OrderStatus.CREATED);
        when(orderTransactionalService.createPendingOrder(any(), any(), any(), any(), any()))
                .thenReturn(created);

        OrderSaga saga = saga(10L, tenantId, orderId, OrderSagaState.RESERVE_STOCK);
        when(orderSagaRepository.save(any())).thenReturn(saga);
        when(orderSagaRepository.findById(10L)).thenReturn(Optional.of(saga));

        doThrow(new InventoryReservationFailedException("out of stock"))
                .when(inventoryClient).reserveStock(any(), any(), any());

        Order failed = order(orderId, tenantId, OrderStatus.FAILED);
        when(orderTransactionalService.markReservationFailed(
                eq(orderId), eq(tenantId), any(), any(), any(), any()))
                .thenReturn(failed);

        assertThatThrownBy(() -> coordinator.createOrder(tenantId, 100L, createRequest("USD"), idemKey))
                .isInstanceOf(InventoryReservationFailedException.class);

        verify(idempotencyService).fail(eq(1L), eq(orderId), any());
    }

    // -----------------------------------------------------------------------
    // payOrder
    // -----------------------------------------------------------------------

    /**
     * Verifies the happy-path payOrder flow: the coordinator charges the payment gateway,
     * confirms stock with the inventory client, and returns a response with CONFIRMED status.
     *
     * @return Asserts response status is CONFIRMED and verifies inventoryClient.confirmStock and idempotencyService interactions.
     */
    @Test
    void payOrder_happyPath_chargesAndConfirmsStock() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-1";
        UUID paymentId = UUID.randomUUID();

        IdempotencyKey iKey = idemKey(2L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "PAY_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        OrderSaga saga = saga(20L, tenantId, orderId, OrderSagaState.CHARGE_PAYMENT);
        when(orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId))
                .thenReturn(Optional.of(saga));
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenReturn(saga);

        Order paymentInProgress = order(orderId, tenantId, OrderStatus.PAYMENT_IN_PROGRESS);
        when(orderTransactionalService.beginPayment(orderId, tenantId)).thenReturn(paymentInProgress);

        when(paymentGatewayService.charge(any(), any(), eq(orderId), eq(tenantId), any(), any()))
                .thenReturn(paymentId);

        Order confirmed = order(orderId, tenantId, OrderStatus.CONFIRMED);
        when(orderTransactionalService.markPaymentSucceeded(
                eq(orderId), eq(tenantId), eq(paymentId), any(), any(), any(), any()))
                .thenReturn(confirmed);

        PayOrderRequest request = new PayOrderRequest();
        request.setProvider("simulated");

        OrderActionResponse response = coordinator.payOrder(tenantId, orderId, request, idemKey);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED.name());
        verify(inventoryClient).confirmStock(orderId, tenantId);
        verify(idempotencyService).complete(eq(2L), eq(orderId), any());
    }

    /**
     * Verifies that a duplicate payOrder call with a COMPLETED idempotency key returns
     * the stored response immediately without invoking the payment gateway.
     *
     * @return Asserts response is the stored instance and verifies paymentGatewayService.charge is never called.
     */
    @Test
    void payOrder_completedIdempotencyKey_replayStoredResponse() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-replay";

        IdempotencyKey iKey = idemKey(2L, IdempotencyStatus.COMPLETED);
        when(idempotencyService.begin(tenantId, idemKey, "PAY_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, false));

        OrderActionResponse stored = OrderActionResponse.builder()
                .orderId(orderId).status("CONFIRMED").message("Payment completed").build();
        when(idempotencyService.replay(iKey, OrderActionResponse.class)).thenReturn(stored);

        PayOrderRequest request = new PayOrderRequest();
        request.setProvider("simulated");

        OrderActionResponse response = coordinator.payOrder(tenantId, orderId, request, idemKey);

        assertThat(response).isSameAs(stored);
        verify(paymentGatewayService, never()).charge(any(), any(), any(), any(), any(), any());
    }

    /**
     * Verifies that when the payment gateway throws during the charge step, the coordinator triggers
     * compensation by releasing reserved stock, does not attempt a refund (paymentId is null at failure
     * time), marks the idempotency key as FAILED, and re-throws the original PaymentFailedException.
     *
     * @return Asserts PaymentFailedException is thrown, inventoryClient.releaseStock is called,
     *         paymentGatewayService.refund is never called, and idempotencyService.fail is called.
     */
    @Test
    void payOrder_paymentGatewayFails_triggersCompensationAndThrowsPaymentFailed() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-pay-fail";

        IdempotencyKey iKey = idemKey(2L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "PAY_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        OrderSaga saga = saga(20L, tenantId, orderId, OrderSagaState.CHARGE_PAYMENT);
        when(orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId))
                .thenReturn(Optional.of(saga));
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenReturn(saga);

        Order paymentInProgress = order(orderId, tenantId, OrderStatus.PAYMENT_IN_PROGRESS);
        when(orderTransactionalService.beginPayment(orderId, tenantId)).thenReturn(paymentInProgress);

        doThrow(new PaymentFailedException("card declined"))
                .when(paymentGatewayService).charge(any(), any(), eq(orderId), eq(tenantId), any(), any());

        Order failed = order(orderId, tenantId, OrderStatus.FAILED);
        when(orderTransactionalService.markPaymentFailed(
                eq(orderId), eq(tenantId), any(), any(), any(), any(), any()))
                .thenReturn(failed);

        PayOrderRequest request = new PayOrderRequest();
        request.setProvider("simulated");

        assertThatThrownBy(() -> coordinator.payOrder(tenantId, orderId, request, idemKey))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("card declined");

        verify(inventoryClient).releaseStock(orderId, tenantId);
        verify(paymentGatewayService, never()).refund(any(), any(), any());
        verify(idempotencyService).fail(eq(2L), eq(orderId), any());
    }

    // -----------------------------------------------------------------------
    // cancelOrder
    // -----------------------------------------------------------------------

    /**
     * Verifies that cancelling a RESERVED order releases the reserved stock via the inventory client
     * before marking the order as CANCELLED and completing the idempotency key.
     *
     * @return Asserts response status is CANCELLED and verifies inventoryClient.releaseStock and idempotencyService.complete.
     */
    @Test
    void cancelOrder_reservedOrder_releasesStockAndCancels() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-cancel-1";

        IdempotencyKey iKey = idemKey(5L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "CANCEL_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        Order reserved = order(orderId, tenantId, OrderStatus.RESERVED);
        when(orderTransactionalService.findOrder(orderId, tenantId)).thenReturn(reserved);

        Order cancelled = order(orderId, tenantId, OrderStatus.CANCELLED);
        when(orderTransactionalService.markCancelled(
                eq(orderId), eq(tenantId), any(), any(), any()))
                .thenReturn(cancelled);

        when(orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)).thenReturn(Optional.empty());

        OrderActionResponse response = coordinator.cancelOrder(tenantId, orderId, idemKey);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(inventoryClient).releaseStock(orderId, tenantId);
        verify(idempotencyService).complete(eq(5L), eq(orderId), any());
    }

    /**
     * Verifies that cancelling a CONFIRMED order does not attempt to release stock via the inventory
     * client, since stock has already been confirmed and releasing it would be incorrect.
     *
     * @return Asserts inventoryClient.releaseStock is never called when the order is in CONFIRMED status.
     */
    @Test
    void cancelOrder_confirmedOrder_skipsReleaseAndCancels() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-cancel-confirmed";

        IdempotencyKey iKey = idemKey(5L, IdempotencyStatus.PROCESSING);
        when(idempotencyService.begin(tenantId, idemKey, "CANCEL_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, true));

        Order confirmed = order(orderId, tenantId, OrderStatus.CONFIRMED);
        when(orderTransactionalService.findOrder(orderId, tenantId)).thenReturn(confirmed);

        Order cancelled = order(orderId, tenantId, OrderStatus.CANCELLED);
        when(orderTransactionalService.markCancelled(
                eq(orderId), eq(tenantId), any(), any(), any()))
                .thenReturn(cancelled);

        when(orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)).thenReturn(Optional.empty());

        coordinator.cancelOrder(tenantId, orderId, idemKey);

        verify(inventoryClient, never()).releaseStock(any(), any());
    }

    /**
     * Verifies that a duplicate cancelOrder call with a COMPLETED idempotency key returns
     * the stored response immediately without releasing stock or modifying order state.
     *
     * @return Asserts response is the stored instance and verifies inventoryClient.releaseStock is never called.
     */
    @Test
    void cancelOrder_completedIdempotencyKey_replayStoredResponse() {
        Long tenantId = 1L;
        UUID orderId = UUID.randomUUID();
        String idemKey = "idem-cancel-replay";

        IdempotencyKey iKey = idemKey(5L, IdempotencyStatus.COMPLETED);
        when(idempotencyService.begin(tenantId, idemKey, "CANCEL_ORDER"))
                .thenReturn(new IdempotencyService.Decision(iKey, false));

        OrderActionResponse stored = OrderActionResponse.builder()
                .orderId(orderId).status("CANCELLED").message("Order cancelled").build();
        when(idempotencyService.replay(iKey, OrderActionResponse.class)).thenReturn(stored);

        OrderActionResponse response = coordinator.cancelOrder(tenantId, orderId, idemKey);

        assertThat(response).isSameAs(stored);
        verify(inventoryClient, never()).releaseStock(any(), any());
    }

    // -----------------------------------------------------------------------
    // resumeInFlightSagas
    // -----------------------------------------------------------------------

    /**
     * Verifies that resumeInFlightSagas performs no repository lookups or saga processing
     * when the sagaEnabled flag is set to false.
     *
     * @return Asserts orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc is never called.
     */
    @Test
    void resumeInFlightSagas_skips_all_work_when_disabled() {
        ReflectionTestUtils.setField(coordinator, "sagaEnabled", false);

        coordinator.resumeInFlightSagas();

        verify(orderSagaRepository, never()).findTop50ByStateInOrderByUpdatedAtAsc(any());
    }

    /**
     * Verifies that a RESERVE_STOCK in-flight saga is picked up by the resume scheduler,
     * calls the inventory client to reserve stock, and advances the order to RESERVED status.
     *
     * @return Asserts inventoryClient.reserveStock and orderTransactionalService.markReservationSucceeded are called.
     */
    @Test
    void resumeInFlightSagas_processes_RESERVE_STOCK_saga_via_inventory_client() {
        UUID orderId = UUID.randomUUID();
        OrderSaga s = saga(20L, 1L, orderId, OrderSagaState.RESERVE_STOCK);
        s.setRequestId("idem-resume-1");
        when(orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc(any()))
                .thenReturn(List.of(s));

        Order createdOrder = order(orderId, 1L, OrderStatus.CREATED);
        when(orderTransactionalService.findOrder(orderId, 1L)).thenReturn(createdOrder);

        when(orderSagaRepository.save(any())).thenReturn(s);
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(s));

        Order reserved = order(orderId, 1L, OrderStatus.RESERVED);
        when(orderTransactionalService.markReservationSucceeded(
                eq(orderId), eq(1L), any(), any(), any(), any()))
                .thenReturn(reserved);

        coordinator.resumeInFlightSagas();

        verify(inventoryClient).reserveStock(eq(orderId), eq(1L), any());
        verify(orderTransactionalService).markReservationSucceeded(
                eq(orderId), eq(1L), any(), any(), any(), any());
    }

    /**
     * Verifies that a RESERVE_STOCK saga whose order has already transitioned to RESERVED
     * is skipped by the resume scheduler without calling the inventory client again.
     *
     * @return Asserts inventoryClient.reserveStock is never called when the order is already RESERVED.
     */
    @Test
    void resumeInFlightSagas_skips_saga_that_is_already_RESERVED() {
        UUID orderId = UUID.randomUUID();
        OrderSaga s = saga(20L, 1L, orderId, OrderSagaState.RESERVE_STOCK);
        when(orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc(any()))
                .thenReturn(List.of(s));

        Order alreadyReserved = order(orderId, 1L, OrderStatus.RESERVED);
        when(orderTransactionalService.findOrder(orderId, 1L)).thenReturn(alreadyReserved);

        when(orderSagaRepository.save(any())).thenReturn(s);
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(s));

        coordinator.resumeInFlightSagas();

        verify(inventoryClient, never()).reserveStock(any(), any(), any());
    }

    /**
     * Verifies that a COMPENSATING saga with a non-null paymentId is picked up by the resume scheduler
     * and triggers both a refund via paymentGatewayService and a stock release via inventoryClient,
     * because payment was captured before the downstream failure.
     *
     * @return Asserts paymentGatewayService.refund and inventoryClient.releaseStock are both called.
     */
    @Test
    void resumeInFlightSagas_compensatingSagaWithPaymentId_refundsAndReleasesStock() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderSaga s = saga(20L, 1L, orderId, OrderSagaState.COMPENSATING);
        s.setPaymentId(paymentId);
        when(orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc(any()))
                .thenReturn(List.of(s));
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(s));
        when(orderSagaRepository.save(any())).thenReturn(s);

        coordinator.resumeInFlightSagas();

        verify(paymentGatewayService).refund(paymentId, orderId, 1L);
        verify(inventoryClient).releaseStock(orderId, 1L);
    }

    /**
     * Verifies that a COMPENSATING saga with a null paymentId (payment was never captured before failure)
     * is picked up by the resume scheduler and releases stock without attempting any refund.
     *
     * @return Asserts inventoryClient.releaseStock is called and paymentGatewayService.refund is never called.
     */
    @Test
    void resumeInFlightSagas_compensatingSagaWithoutPaymentId_onlyReleasesStock() {
        UUID orderId = UUID.randomUUID();
        OrderSaga s = saga(20L, 1L, orderId, OrderSagaState.COMPENSATING);
        // paymentId left null — charge never succeeded before crash
        when(orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc(any()))
                .thenReturn(List.of(s));
        when(orderSagaRepository.findById(20L)).thenReturn(Optional.of(s));
        when(orderSagaRepository.save(any())).thenReturn(s);

        coordinator.resumeInFlightSagas();

        verify(paymentGatewayService, never()).refund(any(), any(), any());
        verify(inventoryClient).releaseStock(orderId, 1L);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a minimal IdempotencyKey with the given ID and status for use in stub setup.
     *
     * @param id     The primary key to assign to the IdempotencyKey.
     * @param status The idempotency status (PROCESSING, COMPLETED, or FAILED) to assign.
     * @return A built IdempotencyKey instance scoped to tenant 1 with fixed requestId and apiName.
     */
    private IdempotencyKey idemKey(Long id, IdempotencyStatus status) {
        return IdempotencyKey.builder()
                .id(id)
                .tenantId(1L)
                .requestId("key")
                .apiName("API")
                .status(status)
                .build();
    }

    /**
     * Builds a minimal Order with the given ID, tenant, and status for use in stub return values.
     *
     * @param id       The UUID to assign as the order identifier.
     * @param tenantId The tenant identifier to scope the order.
     * @param status   The order status to assign.
     * @return A built Order instance with one order item at price 10 USD.
     */
    private Order order(UUID id, Long tenantId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .tenantId(tenantId)
                .userId(100L)
                .status(status)
                .totalAmount(BigDecimal.TEN)
                .currency("USD")
                .orderItems(List.of(
                        OrderItem.builder()
                                .productId(1L)
                                .quantity(1)
                                .priceAtPurchase(BigDecimal.TEN)
                                .build()))
                .paymentAttemptCount(0)
                .build();
    }

    /**
     * Builds a minimal OrderSaga in the given state for use as a stub return value or input argument.
     *
     * @param id       The primary key to assign to the saga.
     * @param tenantId The tenant identifier to scope the saga.
     * @param orderId  The UUID of the associated order.
     * @param state    The saga state (e.g. RESERVE_STOCK, CHARGE_PAYMENT) to assign.
     * @return A built OrderSaga instance with retryCount 0.
     */
    private OrderSaga saga(Long id, Long tenantId, UUID orderId, OrderSagaState state) {
        return OrderSaga.builder()
                .id(id)
                .tenantId(tenantId)
                .orderId(orderId)
                .state(state)
                .retryCount(0)
                .build();
    }

    /**
     * Builds a CreateOrderRequest with one item of quantity 1 at price 10, using the given currency.
     *
     * @param currency The ISO 4217 currency code to assign to the order request.
     * @return A CreateOrderRequest populated with a single OrderItemRequest for product ID 1.
     */
    private CreateOrderRequest createRequest(String currency) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        item.setPrice(BigDecimal.TEN);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCurrency(currency);
        req.setItems(List.of(item));
        return req;
    }
}
