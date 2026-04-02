package huynv.orderservice.saga;

import huynv.orderservice.domain.IdempotencyKey;
import huynv.orderservice.domain.IdempotencyStatus;
import huynv.orderservice.domain.Order;
import huynv.orderservice.domain.OrderStatus;
import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.InventoryReserveItem;
import huynv.orderservice.dto.InventoryReserveRequest;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.OrderItemRequest;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.exception.InvalidOrderStateException;
import huynv.orderservice.exception.PaymentFailedException;
import huynv.orderservice.repository.OrderSagaRepository;
import huynv.orderservice.service.IdempotencyService;
import huynv.orderservice.service.InventoryClient;
import huynv.orderservice.service.OrderTransactionalService;
import huynv.orderservice.service.PaymentGatewayService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * Coordinates persisted order saga execution and resume behavior.
 * Contract:
 * - Step delivery is AT_LEAST_ONCE; side effects must remain idempotent.
 * - Compensation is explicit via COMPENSATING state and retriable rollback actions.
 * - Crash recovery uses persisted saga rows and replay-safe step transitions.
 */
public class OrderSagaCoordinator {

    private static final String API_CREATE_ORDER = "CREATE_ORDER";
    private static final String API_PAY_ORDER = "PAY_ORDER";
    private static final String API_CANCEL_ORDER = "CANCEL_ORDER";
    private static final String ORDER_CREATED_TOTAL = "order_created_total";
    private static final String ORDER_FAILED_TOTAL = "order_failed_total";
    private static final String ORDER_INVENTORY_FAILED_TOTAL = "order_inventory_failed_total";

    private final OrderTransactionalService orderTransactionalService;
    private final IdempotencyService idempotencyService;
    private final InventoryClient inventoryClient;
    private final PaymentGatewayService paymentGatewayService;
    private final OrderSagaRepository orderSagaRepository;
    private final MeterRegistry meterRegistry;
    @Value("${order.saga.enabled:true}")
    private boolean sagaEnabled;

    @Value("${order.payment.default-provider:simulated}")
    private String defaultPaymentProvider;

    /**
     * Executes create-order saga and guarantees deterministic idempotent API behavior.
     *
     * @param tenantId tenant owner identifier propagated by trusted gateway
     * @param userId user identifier owning command side effects
     * @param request validated create-order command payload
     * @param requestId idempotency key from mandatory request header
     * @return deterministic create response for first call and retries
     */
    /**
     * createOrder operation.
     *
     * @param tenantId input parameter
     * @param userId input parameter
     * @param request input parameter
     * @param requestId input parameter
     * @return createOrder result
     */
    public CreateOrderResponse createOrder(Long tenantId, Long userId, CreateOrderRequest request, String requestId) {
        ensureSagaEnabled("createOrder");
        String causationId = UUID.randomUUID().toString();
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, requestId, API_CREATE_ORDER);
        CreateOrderResponse replay = tryReplay(decision.key(), CreateOrderResponse.class);
        if (replay != null) {
            return replay;
        }
        if (!decision.created() && decision.key().getStatus() == IdempotencyStatus.PROCESSING) {
            return buildProcessingCreateResponse(decision.key());
        }

        Order order = orderTransactionalService.createPendingOrder(
                tenantId,
                userId,
                request.getCurrency(),
                calculateTotalAmount(request.getItems()),
                mapOrderItems(request.getItems())
        );
        idempotencyService.bindOrder(decision.key().getId(), order.getId());
        OrderSaga saga = createSaga(tenantId, order.getId(), OrderSagaState.RESERVE_STOCK, requestId, null);
        MDC.put("orderId", order.getId().toString());
        try {
            executeReserveStep(order, saga, requestId, causationId);
            Order reserved = orderTransactionalService.findOrder(order.getId(), tenantId);
            CreateOrderResponse response = CreateOrderResponse.builder()
                    .orderId(reserved.getId())
                    .status(reserved.getStatus().name())
                    .build();
            idempotencyService.complete(decision.key().getId(), reserved.getId(), response);
            meterRegistry.counter(ORDER_CREATED_TOTAL).increment();
            return response;
        } catch (Exception ex) {
            CreateOrderResponse failedResponse = CreateOrderResponse.builder()
                    .orderId(order.getId())
                    .status(OrderStatus.FAILED.name())
                    .build();
            idempotencyService.fail(decision.key().getId(), order.getId(), failedResponse);
            meterRegistry.counter(ORDER_FAILED_TOTAL).increment();
            meterRegistry.counter(ORDER_INVENTORY_FAILED_TOTAL).increment();
            throw ex;
        } finally {
            MDC.remove("orderId");
        }
    }

    /**
     * Executes payment saga from reserved order to confirmed order with compensation safety.
     *
     * @param tenantId tenant owner identifier propagated by trusted gateway
     * @param orderId order identifier targeted by pay API
     * @param request validated payment request payload
     * @param requestId idempotency key from mandatory request header
     * @return deterministic payment response for first call and retries
     */
    /**
     * payOrder operation.
     *
     * @param tenantId input parameter
     * @param orderId input parameter
     * @param request input parameter
     * @param requestId input parameter
     * @return payOrder result
     */
    public OrderActionResponse payOrder(Long tenantId, UUID orderId, PayOrderRequest request, String requestId) {
        ensureSagaEnabled("payOrder");
        String causationId = UUID.randomUUID().toString();
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, requestId, API_PAY_ORDER);
        OrderActionResponse replay = tryReplay(decision.key(), OrderActionResponse.class);
        if (replay != null) {
            return replay;
        }
        idempotencyService.bindOrder(decision.key().getId(), orderId);
        if (!decision.created() && decision.key().getStatus() == IdempotencyStatus.PROCESSING) {
            return buildProcessingActionResponse(tenantId, orderId, "PAYMENT_PROCESSING");
        }

        OrderSaga saga = findOrCreateSaga(tenantId, orderId, OrderSagaState.CHARGE_PAYMENT, requestId, request.getProvider());
        if (saga.getState() == OrderSagaState.COMPLETED) {
            Order order = orderTransactionalService.findOrder(orderId, tenantId);
            OrderActionResponse response = OrderActionResponse.builder()
                    .orderId(order.getId())
                    .status(order.getStatus().name())
                    .message("Order already finalized")
                    .build();
            idempotencyService.complete(decision.key().getId(), order.getId(), response);
            return response;
        }

        MDC.put("orderId", orderId.toString());
        UUID paymentId = saga.getPaymentId();
        try {
            Order started = orderTransactionalService.beginPayment(orderId, tenantId);
            if (started.getStatus() == OrderStatus.CONFIRMED) {
                OrderActionResponse response = OrderActionResponse.builder()
                        .orderId(orderId)
                        .status(OrderStatus.CONFIRMED.name())
                        .message("Order already confirmed")
                        .build();
                idempotencyService.complete(decision.key().getId(), orderId, response);
                return response;
            }

            if (saga.getState() == OrderSagaState.CHARGE_PAYMENT && paymentId == null) {
                Timer.Sample timer = Timer.start(meterRegistry);
                paymentId = paymentGatewayService.charge(request.getProvider(), started.getTotalAmount(), orderId, tenantId);
                timer.stop(meterRegistry.timer("payment.charge.latency"));
                updateSagaAfterCharge(saga.getId(), paymentId, request.getProvider());
                saga = loadSaga(saga.getId());
            }

            if (saga.getState() == OrderSagaState.CONFIRM_STOCK) {
                Timer.Sample timer = Timer.start(meterRegistry);
                inventoryClient.confirmStock(orderId, tenantId);
                timer.stop(meterRegistry.timer("inventory.reserve.latency", "operation", "confirm"));

                Order confirmed = orderTransactionalService.markPaymentSucceeded(
                        orderId,
                        tenantId,
                        saga.getPaymentId(),
                        request.getProvider(),
                        requestId,
                        causationId,
                        requestId
                );
                moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
                OrderActionResponse response = OrderActionResponse.builder()
                        .orderId(confirmed.getId())
                        .status(confirmed.getStatus().name())
                        .message("Payment completed")
                        .build();
                idempotencyService.complete(decision.key().getId(), confirmed.getId(), response);
                return response;
            }

            throw new InvalidOrderStateException("Invalid pay saga state " + saga.getState() + " for order " + orderId);
        } catch (Exception ex) {
            if (ex instanceof InvalidOrderStateException
                    && ex.getMessage() != null
                    && ex.getMessage().contains("already in progress")) {
                return buildProcessingActionResponse(tenantId, orderId, "PAYMENT_PROCESSING");
            }
            handleCompensation(tenantId, orderId, saga, ex.getMessage());
            Order failed = orderTransactionalService.markPaymentFailed(
                    orderId,
                    tenantId,
                    request.getProvider(),
                    ex.getMessage(),
                    requestId,
                    causationId,
                    requestId
            );
            OrderActionResponse response = OrderActionResponse.builder()
                    .orderId(failed.getId())
                    .status(failed.getStatus().name())
                    .message("Payment failed")
                    .build();
            idempotencyService.fail(decision.key().getId(), failed.getId(), response);
            meterRegistry.counter(ORDER_FAILED_TOTAL).increment();
            meterRegistry.counter("saga.step.failure", "step", "PAY_ORDER").increment();
            if (ex instanceof PaymentFailedException) {
                throw (PaymentFailedException) ex;
            }
            throw new PaymentFailedException("Payment flow failed for order " + orderId, ex);
        } finally {
            MDC.remove("orderId");
        }
    }

    /**
     * Executes cancel saga step by releasing reservation and transitioning order safely.
     *
     * @param tenantId tenant owner identifier propagated by trusted gateway
     * @param orderId order identifier targeted by cancel command
     * @param requestId idempotency key from mandatory request header
     * @return deterministic cancellation response for first call and retries
     */
    public OrderActionResponse cancelOrder(Long tenantId, UUID orderId, String requestId) {
        ensureSagaEnabled("cancelOrder");
        String causationId = UUID.randomUUID().toString();
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, requestId, API_CANCEL_ORDER);
        OrderActionResponse replay = tryReplay(decision.key(), OrderActionResponse.class);
        if (replay != null) {
            return replay;
        }
        idempotencyService.bindOrder(decision.key().getId(), orderId);
        if (!decision.created() && decision.key().getStatus() == IdempotencyStatus.PROCESSING) {
            return buildProcessingActionResponse(tenantId, orderId, "CANCEL_PROCESSING");
        }

        MDC.put("orderId", orderId.toString());
        try {
            Order current = orderTransactionalService.findOrder(orderId, tenantId);
            if (current.getStatus() == OrderStatus.RESERVED || current.getStatus() == OrderStatus.PAYMENT_IN_PROGRESS) {
                inventoryClient.releaseStock(orderId, tenantId);
            }
            Order cancelled = orderTransactionalService.markCancelled(orderId, tenantId, requestId, causationId, requestId);
            moveSagaStateIfExists(tenantId, orderId, OrderSagaState.COMPLETED, null);
            OrderActionResponse response = OrderActionResponse.builder()
                    .orderId(cancelled.getId())
                    .status(cancelled.getStatus().name())
                    .message("Order cancelled")
                    .build();
            idempotencyService.complete(decision.key().getId(), cancelled.getId(), response);
            return response;
        } catch (Exception ex) {
            moveSagaStateIfExists(tenantId, orderId, OrderSagaState.COMPENSATING, ex.getMessage());
            meterRegistry.counter("saga.step.failure", "step", "CANCEL_ORDER").increment();
            throw ex;
        } finally {
            MDC.remove("orderId");
        }
    }

    /**
     * Resumes in-flight non-terminal sagas to recover from process crash or transient dependency outage.
     *
     * @param none scheduler callback without explicit runtime parameters
     * @return no return; attempts best-effort step replay for each resumable saga row
     */
    @Scheduled(fixedDelayString = "${saga.resume.delay-ms:5000}")
    @SchedulerLock(name = "order-service-saga-resume", lockAtMostFor = "PT30S", lockAtLeastFor = "PT2S")
    public void resumeInFlightSagas() {
        if (!sagaEnabled) {
            log.warn("Saga resume skipped because order.saga.enabled=false");
            return;
        }
        List<OrderSaga> inFlight = orderSagaRepository.findTop50ByStateInOrderByUpdatedAtAsc(
                Set.of(OrderSagaState.RESERVE_STOCK, OrderSagaState.CHARGE_PAYMENT, OrderSagaState.CONFIRM_STOCK, OrderSagaState.COMPENSATING)
        );
        for (OrderSaga saga : inFlight) {
            try {
                if (saga.getState() == OrderSagaState.RESERVE_STOCK) {
                    Order order = orderTransactionalService.findOrder(saga.getOrderId(), saga.getTenantId());
                    executeReserveStep(order, saga, saga.getRequestId(), UUID.randomUUID().toString());
                    continue;
                }
                if (saga.getState() == OrderSagaState.CONFIRM_STOCK) {
                    inventoryClient.confirmStock(saga.getOrderId(), saga.getTenantId());
                    orderTransactionalService.markPaymentSucceeded(
                            saga.getOrderId(),
                            saga.getTenantId(),
                            saga.getPaymentId(),
                            saga.getPaymentProvider(),
                            saga.getRequestId(),
                            UUID.randomUUID().toString(),
                            saga.getRequestId()
                    );
                    moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
                    continue;
                }
                if (saga.getState() == OrderSagaState.COMPENSATING) {
                    handleCompensation(saga.getTenantId(), saga.getOrderId(), saga, saga.getLastError());
                }
            } catch (Exception ex) {
                meterRegistry.counter("saga.step.failure", "step", "SAGA_RESUME").increment();
                incrementSagaRetry(saga.getId(), ex.getMessage());
                log.warn("Saga resume failed sagaId={} orderId={} state={} reason={}", saga.getId(), saga.getOrderId(), saga.getState(), ex.getMessage());
            }
        }
    }

    /**
     * Executes reserve stock side-effect and transitions saga and order atomically per step.
     *
     * @param order order aggregate snapshot used to build reservation request
     * @param saga persisted saga row tracking current lifecycle step
     * @param correlationId correlation id propagated to outbox event metadata
     * @param causationId causation id propagated to outbox event metadata
     * @return no return; updates order to RESERVED and saga to CHARGE_PAYMENT on success
     */
    /**
     * executeReserveStep operation.
     *
     * @param order input parameter
     * @param saga input parameter
     * @param correlationId input parameter
     * @param causationId input parameter
     * @return performs side effects defined by this operation
     */
    private void executeReserveStep(Order order, OrderSaga saga, String correlationId, String causationId) {
        // Step contract: AT_LEAST_ONCE delivery, reversible by release, crash-safe by persisted state + idempotent inventory API.
        if (order.getStatus() == OrderStatus.RESERVED || order.getStatus() == OrderStatus.CONFIRMED) {
            moveSagaState(saga.getId(), OrderSagaState.CHARGE_PAYMENT, null);
            return;
        }
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            inventoryClient.reserveStock(order.getId(), order.getTenantId(), toInventoryRequest(order, correlationId));
            timer.stop(meterRegistry.timer("inventory.reserve.latency", "operation", "reserve"));
            orderTransactionalService.markReservationSucceeded(
                    order.getId(),
                    order.getTenantId(),
                    order.getId().toString(),
                    correlationId,
                    causationId,
                    saga.getRequestId()
            );
            moveSagaState(saga.getId(), OrderSagaState.CHARGE_PAYMENT, null);
        } catch (Exception ex) {
            timer.stop(meterRegistry.timer("inventory.reserve.latency", "operation", "reserve"));
            moveSagaState(saga.getId(), OrderSagaState.COMPENSATING, ex.getMessage());
            orderTransactionalService.markReservationFailed(
                    order.getId(),
                    order.getTenantId(),
                    ex.getMessage(),
                    correlationId,
                    causationId,
                    saga.getRequestId()
            );
            try {
                inventoryClient.releaseStock(order.getId(), order.getTenantId());
            } catch (Exception releaseEx) {
                log.warn("Inventory release after reserve failure failed orderId={} reason={}", order.getId(), releaseEx.getMessage());
            }
            orderTransactionalService.rollbackUnreservedOrder(order.getId(), order.getTenantId());
            throw ex;
        }
    }

    /**
     * Executes compensating actions for charge-first ordering when confirm step fails.
     *
     * @param tenantId tenant identifier required by external clients
     * @param orderId order identifier used by compensation side effects
     * @param saga persisted saga state containing payment id when available
     * @param reason root error reason to persist in saga diagnostics
     * @return no return; moves saga to COMPLETED if compensation succeeds
     */
    /**
     * handleCompensation operation.
     *
     * @param tenantId input parameter
     * @param orderId input parameter
     * @param saga input parameter
     * @param reason input parameter
     * @return performs side effects defined by this operation
     */
    private void handleCompensation(Long tenantId, UUID orderId, OrderSaga saga, String reason) {
        // Step contract: AT_LEAST_ONCE retries, compensation attempts refund then release, crash-safe by persisted paymentId.
        moveSagaState(saga.getId(), OrderSagaState.COMPENSATING, reason);
        try {
            if (saga.getPaymentId() != null) {
                paymentGatewayService.refund(saga.getPaymentId(), orderId, tenantId);
            }
            inventoryClient.releaseStock(orderId, tenantId);
            moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
        } catch (Exception compensationEx) {
            incrementSagaRetry(saga.getId(), compensationEx.getMessage());
            throw compensationEx;
        }
    }

    /**
     * Persists initial saga row in dedicated transaction for crash-safe step continuation.
     *
     * @param tenantId tenant owner identifier propagated by trusted gateway
     * @param orderId order identifier tied to saga lifecycle
     * @param state initial state to execute next
     * @param requestId idempotency key for command flow
     * @param provider optional payment provider saved for pay-step resumes
     * @return persisted saga row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * createSaga operation.
     *
     * @param tenantId input parameter
     * @param orderId input parameter
     * @param state input parameter
     * @param requestId input parameter
     * @param provider input parameter
     * @return createSaga result
     */
    public OrderSaga createSaga(Long tenantId, UUID orderId, OrderSagaState state, String requestId, String provider) {
        OrderSaga saga = OrderSaga.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .state(state)
                .requestId(requestId)
                .paymentProvider(provider)
                .retryCount(0)
                .build();
        return orderSagaRepository.save(saga);
    }

    /**
     * Loads existing saga or creates one when order enters orchestration for first time.
     *
     * @param tenantId tenant owner identifier propagated by trusted gateway
     * @param orderId order identifier tied to saga lifecycle
     * @param state default state used when saga does not exist yet
     * @param requestId idempotency key for command flow
     * @param provider payment provider for pay flow resume
     * @return existing or newly created saga row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * findOrCreateSaga operation.
     *
     * @param tenantId input parameter
     * @param orderId input parameter
     * @param state input parameter
     * @param requestId input parameter
     * @param provider input parameter
     * @return findOrCreateSaga result
     */
    public OrderSaga findOrCreateSaga(Long tenantId, UUID orderId, OrderSagaState state, String requestId, String provider) {
        return orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .orElseGet(() -> createSaga(tenantId, orderId, state, requestId, provider));
    }

    /**
     * Loads saga by id for step transitions executed across separate transactions.
     *
     * @param sagaId saga row identifier
     * @return latest persisted saga row
     */
    @Transactional(readOnly = true)
    public OrderSaga loadSaga(Long sagaId) {
        return orderSagaRepository.findById(sagaId).orElseThrow();
    }

    /**
     * Moves saga state deterministically while updating diagnostics for failures and retries.
     *
     * @param sagaId saga row identifier
     * @param nextState next finite-state value after step result
     * @param lastError optional last failure reason
     * @return no return; persists transition for crash-safe resume
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * moveSagaState operation.
     *
     * @param sagaId input parameter
     * @param nextState input parameter
     * @param lastError input parameter
     * @return performs side effects defined by this operation
     */
    public void moveSagaState(Long sagaId, OrderSagaState nextState, String lastError) {
        OrderSaga saga = orderSagaRepository.findById(sagaId).orElseThrow();
        saga.setState(nextState);
        saga.setLastError(lastError);
        orderSagaRepository.save(saga);
        log.info("Saga step transitioned sagaId={} orderId={} state={}", saga.getId(), saga.getOrderId(), nextState);
    }

    /**
     * Moves saga state when saga exists for given order and tenant.
     *
     * @param tenantId tenant scope of saga row
     * @param orderId order identifier tied to saga lifecycle
     * @param nextState target state after command execution
     * @param lastError optional last failure reason
     * @return no return; updates existing saga and no-ops when saga is absent
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * moveSagaStateIfExists operation.
     *
     * @param tenantId input parameter
     * @param orderId input parameter
     * @param nextState input parameter
     * @param lastError input parameter
     * @return performs side effects defined by this operation
     */
    public void moveSagaStateIfExists(Long tenantId, UUID orderId, OrderSagaState nextState, String lastError) {
        orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .ifPresent(saga -> {
                    saga.setState(nextState);
                    saga.setLastError(lastError);
                    orderSagaRepository.save(saga);
                });
    }

    /**
     * Stores payment metadata after charge step so confirm can resume safely post-crash.
     *
     * @param sagaId saga row identifier
     * @param paymentId charged payment identifier
     * @param provider payment provider used for charging and refund compensation
     * @return no return; transitions saga to CONFIRM_STOCK
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * updateSagaAfterCharge operation.
     *
     * @param sagaId input parameter
     * @param paymentId input parameter
     * @param provider input parameter
     * @return performs side effects defined by this operation
     */
    public void updateSagaAfterCharge(Long sagaId, UUID paymentId, String provider) {
        OrderSaga saga = orderSagaRepository.findById(sagaId).orElseThrow();
        saga.setPaymentId(paymentId);
        saga.setPaymentProvider(provider);
        saga.setState(OrderSagaState.CONFIRM_STOCK);
        saga.setLastError(null);
        orderSagaRepository.save(saga);
    }

    /**
     * Increments retry count and stores latest error to support operational triage.
     *
     * @param sagaId saga row identifier
     * @param lastError latest failure message from failed step execution
     * @return no return; updates retry counter and state diagnostics
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementSagaRetry(Long sagaId, String lastError) {
        OrderSaga saga = orderSagaRepository.findById(sagaId).orElseThrow();
        saga.setRetryCount(saga.getRetryCount() + 1);
        saga.setLastError(lastError);
        orderSagaRepository.save(saga);
    }

    /**
     * Reconstructs deterministic response for in-flight create command without 409 conflicts.
     *
     * @param key idempotency key row currently marked as PROCESSING
     * @return stable create response with order reference when available
     */
    private CreateOrderResponse buildProcessingCreateResponse(IdempotencyKey key) {
        return CreateOrderResponse.builder()
                .orderId(key.getOrderId())
                .status("PROCESSING")
                .build();
    }

    /**
     * Reconstructs deterministic response for in-flight pay/cancel commands without conflicts.
     *
     * @param tenantId tenant scope used to read current order state safely
     * @param orderId order identifier targeted by command
     * @param message stable processing message for API clients
     * @return stable action response using latest persisted order status
     */
    private OrderActionResponse buildProcessingActionResponse(Long tenantId, UUID orderId, String message) {
        Order order = orderTransactionalService.findOrder(orderId, tenantId);
        return OrderActionResponse.builder()
                .orderId(orderId)
                .status(order.getStatus().name())
                .message(message)
                .build();
    }

    /**
     * Replays persisted idempotent payload when command already reached terminal state.
     *
     * @param key idempotency key row for current tenant and api command
     * @param type target DTO class for deterministic payload reconstruction
     * @param <T> strongly typed response DTO returned to controller
     * @return replayed payload for COMPLETED/FAILED records or null if still processing
     */
    private <T> T tryReplay(IdempotencyKey key, Class<T> type) {
        if (key.getStatus() == IdempotencyStatus.COMPLETED || key.getStatus() == IdempotencyStatus.FAILED) {
            return idempotencyService.replay(key, type);
        }
        return null;
    }

    /**
     * Calculates immutable total amount from incoming item snapshot list.
     *
     * @param items validated API item requests for create command
     * @return total amount used for order aggregate creation and payment flow
     */
    private BigDecimal calculateTotalAmount(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Maps API item requests to immutable domain snapshots persisted with order.
     *
     * @param items validated API item requests for create command
     * @return mapped domain snapshots preserving price-at-purchase values
     */
    private List<huynv.orderservice.domain.OrderItem> mapOrderItems(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> huynv.orderservice.domain.OrderItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPrice())
                        .build())
                .toList();
    }

    /**
     * Builds reserve request payload from current order snapshot for inventory-service.
     *
     * @param order order aggregate containing item snapshots and tenant id
     * @return reserve request payload accepted by inventory internal contract
     */
    private InventoryReserveRequest toInventoryRequest(Order order, String correlationId) {
        return InventoryReserveRequest.builder()
                .orderId(order.getId())
                .tenantId(order.getTenantId())
                .items(order.getOrderItems().stream()
                        .map(item -> InventoryReserveItem.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .paymentProvider(defaultPaymentProvider)
                .idempotencyKey(correlationId)
                .correlationId(correlationId)
                .traceId(correlationId)
                .build();
    }

    /**
     * Enforces saga kill-switch policy by failing fast when orchestration is disabled.
     *
     * @param operation operation name used for explicit failure logs and exception text
     * @return no return; throws InvalidOrderStateException when kill switch is disabled
     */
    private void ensureSagaEnabled(String operation) {
        if (!sagaEnabled) {
            log.error("Saga execution blocked operation={} because order.saga.enabled=false", operation);
            throw new InvalidOrderStateException("Saga execution disabled by configuration");
        }
    }
}
