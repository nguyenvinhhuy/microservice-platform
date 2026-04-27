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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates persisted order saga execution and resume behavior.
 * Contract: step delivery is at-least-once, compensation is explicit via COMPENSATING state,
 * and crash recovery relies on persisted saga rows plus replay-safe transitions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
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
    private final ObjectProvider<OrderSagaCoordinator> selfProvider;
    @Value("${order.saga.enabled:true}")
    private boolean sagaEnabled;

    @Value("${order.payment.default-provider:simulated}")
    private String defaultPaymentProvider;

    /**
     * Executes the create-order saga and guarantees deterministic idempotent API behavior.
     *
     * @param tenantId The tenant owner identifier propagated by the trusted gateway.
     * @param userId The user identifier that owns the command side effects.
     * @param request The validated create-order command payload.
     * @param idempotencyKey The idempotency key from the mandatory request header.
     * @return Returns the deterministic create response for the first call and for retries.
     */
    public CreateOrderResponse createOrder(Long tenantId, Long userId, CreateOrderRequest request, String idempotencyKey) {
        ensureSagaEnabled("createOrder");
        String causationId = UUID.randomUUID().toString();
        String correlationId = currentCorrelationId(idempotencyKey);
        String traceId = currentTraceId(correlationId);
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, idempotencyKey, API_CREATE_ORDER);
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
        OrderSaga saga = self().createSaga(tenantId, order.getId(), OrderSagaState.RESERVE_STOCK, idempotencyKey, null);
        MDC.put("orderId", order.getId().toString());
        try {
            executeReserveStep(order, saga, correlationId, traceId, causationId);
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
     * Executes the payment saga from a reserved order to a confirmed order with compensation safety.
     *
     * @param tenantId The tenant owner identifier propagated by the trusted gateway.
     * @param orderId The order identifier targeted by the pay API.
     * @param request The validated payment request payload.
     * @param idempotencyKey The idempotency key from the mandatory request header.
     * @return Returns the deterministic payment response for the first call and for retries.
     */
    public OrderActionResponse payOrder(Long tenantId, UUID orderId, PayOrderRequest request, String idempotencyKey) {
        ensureSagaEnabled("payOrder");
        String causationId = UUID.randomUUID().toString();
        String correlationId = currentCorrelationId(idempotencyKey);
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, idempotencyKey, API_PAY_ORDER);
        OrderActionResponse replay = tryReplay(decision.key(), OrderActionResponse.class);
        if (replay != null) {
            return replay;
        }
        idempotencyService.bindOrder(decision.key().getId(), orderId);
        if (!decision.created() && decision.key().getStatus() == IdempotencyStatus.PROCESSING) {
            return buildProcessingActionResponse(tenantId, orderId, "PAYMENT_PROCESSING");
        }

        OrderSaga saga = self().findOrCreateSaga(tenantId, orderId, OrderSagaState.CHARGE_PAYMENT, idempotencyKey, request.getProvider());
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
                paymentId = paymentGatewayService.charge(request.getProvider(), started.getTotalAmount(), orderId, tenantId, idempotencyKey, correlationId);
                timer.stop(meterRegistry.timer("payment.charge.latency"));
                self().updateSagaAfterCharge(saga.getId(), paymentId, request.getProvider());
                saga = self().loadSaga(saga.getId());
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
                        correlationId,
                        causationId,
                        idempotencyKey
                );
                self().moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
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
                    correlationId,
                    causationId,
                    idempotencyKey
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
     * @param idempotencyKey idempotency key from mandatory request header
     * @return deterministic cancellation response for first call and retries
     */
    public OrderActionResponse cancelOrder(Long tenantId, UUID orderId, String idempotencyKey) {
        ensureSagaEnabled("cancelOrder");
        String causationId = UUID.randomUUID().toString();
        String correlationId = currentCorrelationId(idempotencyKey);
        IdempotencyService.Decision decision = idempotencyService.begin(tenantId, idempotencyKey, API_CANCEL_ORDER);
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
            Order cancelled = orderTransactionalService.markCancelled(orderId, tenantId, correlationId, causationId, idempotencyKey);
            self().moveSagaStateIfExists(tenantId, orderId, OrderSagaState.COMPLETED, null);
            OrderActionResponse response = OrderActionResponse.builder()
                    .orderId(cancelled.getId())
                    .status(cancelled.getStatus().name())
                    .message("Order cancelled")
                    .build();
            idempotencyService.complete(decision.key().getId(), cancelled.getId(), response);
            return response;
        } catch (Exception ex) {
            self().moveSagaStateIfExists(tenantId, orderId, OrderSagaState.COMPENSATING, ex.getMessage());
            meterRegistry.counter("saga.step.failure", "step", "CANCEL_ORDER").increment();
            throw ex;
        } finally {
            MDC.remove("orderId");
        }
    }

    /**
     * Resumes in-flight non-terminal sagas to recover from process crashes or transient dependency outages.
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
                    String correlationId = currentCorrelationId(saga.getRequestId());
                    String traceId = currentTraceId(correlationId);
                    executeReserveStep(order, saga, correlationId, traceId, UUID.randomUUID().toString());
                    continue;
                }
                if (saga.getState() == OrderSagaState.CONFIRM_STOCK) {
                    String correlationId = currentCorrelationId(saga.getRequestId());
                    inventoryClient.confirmStock(saga.getOrderId(), saga.getTenantId());
                    orderTransactionalService.markPaymentSucceeded(
                            saga.getOrderId(),
                            saga.getTenantId(),
                            saga.getPaymentId(),
                            saga.getPaymentProvider(),
                            correlationId,
                            UUID.randomUUID().toString(),
                            saga.getRequestId()
                    );
                    self().moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
                    continue;
                }
                if (saga.getState() == OrderSagaState.COMPENSATING) {
                    handleCompensation(saga.getTenantId(), saga.getOrderId(), saga, saga.getLastError());
                }
            } catch (Exception ex) {
                meterRegistry.counter("saga.step.failure", "step", "SAGA_RESUME").increment();
                self().incrementSagaRetry(saga.getId(), ex.getMessage());
                log.warn("Saga resume failed sagaId={} orderId={} state={} reason={}", saga.getId(), saga.getOrderId(), saga.getState(), ex.getMessage());
            }
        }
    }

    /**
     * Executes the reserve-stock side effect and transitions saga and order state per step.
     *
     * @param order The order aggregate snapshot used to build the reservation request.
     * @param saga The persisted saga row that tracks the current lifecycle step.
     * @param correlationId The correlation id propagated to outbox event metadata.
     * @param traceId The trace id propagated to downstream reservation request metadata.
     * @param causationId The causation id propagated to outbox event metadata.
     */
    private void executeReserveStep(Order order, OrderSaga saga, String correlationId, String traceId, String causationId) {
        // Step contract: AT_LEAST_ONCE delivery, reversible by release, crash-safe by persisted state + idempotent inventory API.
        if (order.getStatus() == OrderStatus.RESERVED || order.getStatus() == OrderStatus.CONFIRMED) {
            self().moveSagaState(saga.getId(), OrderSagaState.CHARGE_PAYMENT, null);
            return;
        }
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            inventoryClient.reserveStock(order.getId(), order.getTenantId(), toInventoryRequest(order, saga.getRequestId(), correlationId, traceId));
            timer.stop(meterRegistry.timer("inventory.reserve.latency", "operation", "reserve"));
            orderTransactionalService.markReservationSucceeded(
                    order.getId(),
                    order.getTenantId(),
                    order.getId().toString(),
                    correlationId,
                    causationId,
                    saga.getRequestId()
            );
            self().moveSagaState(saga.getId(), OrderSagaState.CHARGE_PAYMENT, null);
        } catch (Exception ex) {
            timer.stop(meterRegistry.timer("inventory.reserve.latency", "operation", "reserve"));
            self().moveSagaState(saga.getId(), OrderSagaState.COMPENSATING, ex.getMessage());
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
     * Executes compensating actions for charge-first ordering when the confirm step fails.
     *
     * @param tenantId The tenant identifier required by downstream clients.
     * @param orderId The order identifier used by compensation side effects.
     * @param saga The persisted saga state containing the payment id when available.
     * @param reason The root error reason to persist in saga diagnostics.
     */
    private void handleCompensation(Long tenantId, UUID orderId, OrderSaga saga, String reason) {
        // Step contract: AT_LEAST_ONCE retries, compensation attempts refund then release, crash-safe by persisted paymentId.
        self().moveSagaState(saga.getId(), OrderSagaState.COMPENSATING, reason);
        try {
            if (saga.getPaymentId() != null) {
                paymentGatewayService.refund(saga.getPaymentId(), orderId, tenantId);
            }
            inventoryClient.releaseStock(orderId, tenantId);
            self().moveSagaState(saga.getId(), OrderSagaState.COMPLETED, null);
        } catch (Exception compensationEx) {
            self().incrementSagaRetry(saga.getId(), compensationEx.getMessage());
            throw compensationEx;
        }
    }

    /**
     * Persists the initial saga row in a dedicated transaction for crash-safe step continuation.
     *
     * @param tenantId The tenant owner identifier propagated by the trusted gateway.
     * @param orderId The order identifier tied to the saga lifecycle.
     * @param state The initial state to execute next.
     * @param requestId The idempotency key for the command flow.
     * @param provider The optional payment provider saved for pay-step resumes.
     * @return Returns the persisted saga row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
     * Loads an existing saga or creates one when an order enters orchestration for the first time.
     *
     * @param tenantId The tenant owner identifier propagated by the trusted gateway.
     * @param orderId The order identifier tied to the saga lifecycle.
     * @param state The default state used when a saga does not exist yet.
     * @param requestId The idempotency key for the command flow.
     * @param provider The payment provider retained for pay-flow resume.
     * @return Returns the existing or newly created saga row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderSaga findOrCreateSaga(Long tenantId, UUID orderId, OrderSagaState state, String requestId, String provider) {
        return orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .orElseGet(() -> self().createSaga(tenantId, orderId, state, requestId, provider));
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
     * Moves the saga state deterministically while updating diagnostics for failures and retries.
     *
     * @param sagaId The saga row identifier.
     * @param nextState The next finite-state value after the step result.
     * @param lastError The optional last failure reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moveSagaState(Long sagaId, OrderSagaState nextState, String lastError) {
        OrderSaga saga = orderSagaRepository.findById(sagaId).orElseThrow();
        saga.setState(nextState);
        saga.setLastError(lastError);
        orderSagaRepository.save(saga);
        log.info("Saga step transitioned sagaId={} orderId={} state={}", saga.getId(), saga.getOrderId(), nextState);
    }

    /**
     * Moves the saga state when a saga exists for the given order and tenant.
     *
     * @param tenantId The tenant scope of the saga row.
     * @param orderId The order identifier tied to the saga lifecycle.
     * @param nextState The target state after command execution.
     * @param lastError The optional last failure reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moveSagaStateIfExists(Long tenantId, UUID orderId, OrderSagaState nextState, String lastError) {
        orderSagaRepository.findByTenantIdAndOrderId(tenantId, orderId)
                .ifPresent(saga -> {
                    saga.setState(nextState);
                    saga.setLastError(lastError);
                    orderSagaRepository.save(saga);
                });
    }

    /**
     * Stores payment metadata after the charge step so confirm can resume safely after a crash.
     *
     * @param sagaId The saga row identifier.
     * @param paymentId The charged payment identifier.
     * @param provider The payment provider used for charging and refund compensation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSagaAfterCharge(Long sagaId, UUID paymentId, String provider) {
        OrderSaga saga = orderSagaRepository.findById(sagaId).orElseThrow();
        saga.setPaymentId(paymentId);
        saga.setPaymentProvider(provider);
        saga.setState(OrderSagaState.CONFIRM_STOCK);
        saga.setLastError(null);
        orderSagaRepository.save(saga);
    }

    /**
     * Increments the retry count and stores the latest error to support operational triage.
     *
     * @param sagaId The saga row identifier.
     * @param lastError The latest failure message from the failed step execution.
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
     * Builds the reserve-request payload from the current order snapshot for inventory-service.
     *
     * @param order The order aggregate containing item snapshots and tenant id.
     * @param idempotencyKey The idempotency key propagated to inventory-service.
     * @param correlationId The correlation identifier propagated to inventory-service.
     * @param traceId The trace identifier propagated to inventory-service.
     * @return Returns the reserve-request payload accepted by the inventory internal contract.
     */
    private InventoryReserveRequest toInventoryRequest(Order order, String idempotencyKey, String correlationId, String traceId) {
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
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .traceId(traceId)
                .build();
    }

    /**
     * Resolves the current correlation identifier from MDC and falls back to a stable command key when absent.
     *
     * @param fallback Stable command or saga key used when request-scoped correlation is unavailable.
     * @return Returns the effective correlation identifier for downstream calls and events.
     */
    private String currentCorrelationId(String fallback) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return fallback;
    }

    /**
     * Resolves the current trace identifier from MDC and falls back to a stable correlation value when absent.
     *
     * @param fallback Stable correlation value used when no active trace id exists.
     * @return Returns the effective trace identifier for downstream metadata.
     */
    private String currentTraceId(String fallback) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return fallback;
    }

    /**
     * Enforces the saga kill-switch policy by failing fast when orchestration is disabled.
     *
     * @param operation The operation name used for explicit failure logs and exception text.
     */
    private void ensureSagaEnabled(String operation) {
        if (!sagaEnabled) {
            log.error("Saga execution blocked operation={} because order.saga.enabled=false", operation);
            throw new InvalidOrderStateException("Saga execution disabled by configuration");
        }
    }

    /**
     * Resolves the proxied coordinator bean so REQUIRES_NEW transactional methods are invoked through Spring AOP.
     *
     * @return Returns the proxied coordinator bean.
     */
    private OrderSagaCoordinator self() {
        return selfProvider.getObject();
    }
}
