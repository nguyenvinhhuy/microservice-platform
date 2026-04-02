package huynv.orderservice.service;

import huynv.event.order.OrderCancelledEvent;
import huynv.event.order.OrderCreatedEvent;
import huynv.event.order.OrderFailedEvent;
import huynv.event.order.OrderPaidEvent;
import huynv.orderservice.domain.Order;
import huynv.orderservice.domain.OrderItem;
import huynv.orderservice.domain.OrderPayment;
import huynv.orderservice.domain.OrderStatus;
import huynv.orderservice.domain.PaymentStatus;
import huynv.orderservice.exception.InvalidOrderStateException;
import huynv.orderservice.exception.OrderNotFoundException;
import huynv.orderservice.repository.OrderPaymentRepository;
import huynv.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderTransactionalService {

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OutboxService outboxService;

    /**
     * Persists created order snapshot in one local transaction before external effects.
     *
     * @param tenantId tenant owner identifier propagated by gateway trust boundary
     * @param userId user identifier used for ownership and audit context
     * @param currency ISO currency code from API contract
     * @param totalAmount calculated immutable order total
     * @param items product snapshots persisted for price and quantity consistency
     * @return created order aggregate in CREATED state
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * createPendingOrder operation.
     *
     * @param tenantId input parameter
     * @param userId input parameter
     * @param currency input parameter
     * @param totalAmount input parameter
     * @param items input parameter
     * @return createPendingOrder result
     */
    public Order createPendingOrder(Long tenantId, Long userId, String currency, BigDecimal totalAmount, List<OrderItem> items) {
        Order order = Order.builder()
                .tenantId(tenantId)
                .userId(userId)
                .status(OrderStatus.CREATED)
                .currency(currency)
                .totalAmount(totalAmount)
                .orderItems(items)
                .build();
        return orderRepository.save(order);
    }

    /**
     * Marks a successful stock reservation and emits an order.created integration event into the transactional outbox.
     *
     * @param orderId Order identifier being updated.
     * @param tenantId Tenant identifier used to validate order ownership.
     * @param reservationReference Reservation reference returned by inventory-service.
     * @param correlationId Correlation identifier propagated across services for one business flow.
     * @param causationId Causation identifier representing the triggering local command.
     * @param idempotencyKey Idempotency key used to deduplicate producer-side effects.
     * @return Returns the updated order aggregate persisted in RESERVED status.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markReservationSucceeded(UUID orderId,
                                          Long tenantId,
                                          String reservationReference,
                                          String correlationId,
                                          String causationId,
                                          String idempotencyKey) {
        Order order = findOrder(orderId, tenantId);
        order.markReservationSucceeded(reservationReference);
        Order updated = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                1,
                correlationId,
                causationId,
                updated.getId(),
                updated.getTenantId(),
                updated.getUserId(),
                updated.getStatus().name(),
                updated.getTotalAmount(),
                updated.getCurrency(),
                Instant.now()
        );
        outboxService.enqueue("Order", updated.getId().toString(), "OrderCreatedEvent", event, correlationId, causationId, idempotencyKey);
        return updated;
    }

    /**
     * Marks reservation failure and records failed event in transactional outbox.
     *
     * @param orderId order identifier under mutation
     * @param tenantId tenant scope for secure ownership validation
     * @param reason failure reason persisted for diagnostics
     * @param correlationId cross-service request correlation id
     * @param causationId causation id representing triggering local command
     * @param idempotencyKey request id key for deterministic replay
     * @return order aggregate transitioned to FAILED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markReservationFailed(UUID orderId,
                                       Long tenantId,
                                       String reason,
                                       String correlationId,
                                       String causationId,
                                       String idempotencyKey) {
        Order order = findOrder(orderId, tenantId);
        order.markReservationFailed(reason);
        Order updated = orderRepository.save(order);

        OrderFailedEvent event = new OrderFailedEvent(
                UUID.randomUUID(),
                1,
                correlationId,
                causationId,
                updated.getId(),
                updated.getTenantId(),
                updated.getUserId(),
                updated.getStatus().name(),
                reason,
                Instant.now()
        );
        outboxService.enqueue("Order", updated.getId().toString(), "OrderFailedEvent", event, correlationId, causationId, idempotencyKey);
        return updated;
    }

    /**
     * Locks one order row and moves it to payment in progress exactly once.
     *
     * @param orderId order identifier being charged
     * @param tenantId tenant scope for secure ownership validation
     * @return order aggregate locked and transitioned to PAYMENT_IN_PROGRESS
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order beginPayment(UUID orderId, Long tenantId) {
        Order order = orderRepository.findByIdAndTenantIdAndStatus(orderId, tenantId, OrderStatus.RESERVED)
                .orElseGet(() -> findOrder(orderId, tenantId));

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return order;
        }
        if (order.getStatus() == OrderStatus.PAYMENT_IN_PROGRESS) {
            throw new InvalidOrderStateException("Payment is already in progress for order " + orderId);
        }
        order.beginPaymentAttempt();
        return orderRepository.save(order);
    }

    /**
     * Marks successful payment confirmation and appends paid event to outbox.
     *
     * @param orderId order identifier being finalized
     * @param tenantId tenant scope for secure ownership validation
     * @param paymentId payment reference returned by gateway
     * @param provider payment provider identifier
     * @param correlationId cross-service request correlation id
     * @param causationId causation id representing triggering local command
     * @param idempotencyKey request id key for deterministic replay
     * @return order aggregate transitioned to CONFIRMED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markPaymentSucceeded(UUID orderId,
                                      Long tenantId,
                                      UUID paymentId,
                                      String provider,
                                      String correlationId,
                                      String causationId,
                                      String idempotencyKey) {
        Order order = findOrder(orderId, tenantId);
        order.markPaid();
        upsertPayment(order, tenantId, paymentId, provider, PaymentStatus.SUCCESS);
        Order updated = orderRepository.save(order);

        OrderPaidEvent event = new OrderPaidEvent(
                UUID.randomUUID(),
                1,
                correlationId,
                causationId,
                updated.getId(),
                updated.getTenantId(),
                updated.getUserId(),
                updated.getStatus().name(),
                paymentId,
                Instant.now()
        );
        outboxService.enqueue("Order", updated.getId().toString(), "OrderPaidEvent", event, correlationId, causationId, idempotencyKey);
        return updated;
    }

    /**
     * Marks payment failure and appends failed event to outbox in same transaction.
     *
     * @param orderId order identifier under mutation
     * @param tenantId tenant scope for secure ownership validation
     * @param provider payment provider identifier
     * @param reason failure reason persisted for diagnostics
     * @param correlationId cross-service request correlation id
     * @param causationId causation id representing triggering local command
     * @param idempotencyKey request id key for deterministic replay
     * @return order aggregate transitioned to FAILED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markPaymentFailed(UUID orderId,
                                   Long tenantId,
                                   String provider,
                                   String reason,
                                   String correlationId,
                                   String causationId,
                                   String idempotencyKey) {
        Order order = findOrder(orderId, tenantId);
        if (order.getStatus() == OrderStatus.PAYMENT_IN_PROGRESS) {
            order.markPaymentFailed(reason);
        } else if (order.getStatus() != OrderStatus.FAILED) {
            throw new InvalidOrderStateException("Cannot mark payment failed for order " + orderId + " with status " + order.getStatus());
        }
        upsertPayment(order, tenantId, UUID.randomUUID(), provider, PaymentStatus.FAILED);
        Order updated = orderRepository.save(order);

        OrderFailedEvent event = new OrderFailedEvent(
                UUID.randomUUID(),
                1,
                correlationId,
                causationId,
                updated.getId(),
                updated.getTenantId(),
                updated.getUserId(),
                updated.getStatus().name(),
                reason,
                Instant.now()
        );
        outboxService.enqueue("Order", updated.getId().toString(), "OrderFailedEvent", event, correlationId, causationId, idempotencyKey);
        return updated;
    }

    /**
     * Moves order to compensating state when side-effect rollback cannot complete immediately.
     *
     * @param orderId order identifier under compensation flow
     * @param tenantId tenant scope for secure ownership validation
     * @param reason failure reason from compensation attempt
     * @return order aggregate transitioned to COMPENSATING
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * markCompensating operation.
     *
     * @param orderId input parameter
     * @param tenantId input parameter
     * @param reason input parameter
     * @return markCompensating result
     */
    public Order markCompensating(UUID orderId, Long tenantId, String reason) {
        Order order = findOrder(orderId, tenantId);
        order.markCompensating(reason);
        return orderRepository.save(order);
    }

    /**
     * Cancels order and appends cancellation event to transactional outbox.
     *
     * @param orderId order identifier being cancelled
     * @param tenantId tenant scope for secure ownership validation
     * @param correlationId cross-service request correlation id
     * @param causationId causation id representing triggering local command
     * @param idempotencyKey request id key for deterministic replay
     * @return order aggregate transitioned to CANCELLED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markCancelled(UUID orderId,
                               Long tenantId,
                               String correlationId,
                               String causationId,
                               String idempotencyKey) {
        Order order = findOrder(orderId, tenantId);
        order.markCancelled();
        Order updated = orderRepository.save(order);

        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(),
                1,
                correlationId,
                causationId,
                updated.getId(),
                updated.getTenantId(),
                updated.getUserId(),
                updated.getStatus().name(),
                Instant.now()
        );
        outboxService.enqueue("Order", updated.getId().toString(), "OrderCancelledEvent", event, correlationId, causationId, idempotencyKey);
        return updated;
    }

    /**
     * Loads one tenant-owned order and blocks cross-tenant reads by default.
     *
     * @param orderId order identifier requested by command flow
     * @param tenantId tenant scope for secure ownership validation
     * @return existing order aggregate owned by tenant
     */
    @Transactional(readOnly = true)
    public Order findOrder(UUID orderId, Long tenantId) {
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found for tenant " + tenantId));
    }

    /**
     * Upserts payment snapshot row for idempotent payment result persistence.
     *
     * @param order aggregate owner of payment snapshot
     * @param tenantId tenant scope guard for payment lookup
     * @param paymentId payment reference associated with gateway transaction
     * @param provider payment provider identifier
     * @param status payment result status stored for reconciliation
     * @return no return; payment row is updated in the current transaction
     */
    /**
     * upsertPayment operation.
     *
     * @param order input parameter
     * @param tenantId input parameter
     * @param paymentId input parameter
     * @param provider input parameter
     * @param status input parameter
     * @return performs side effects defined by this operation
     */
    private void upsertPayment(Order order, Long tenantId, UUID paymentId, String provider, PaymentStatus status) {
        OrderPayment payment = orderPaymentRepository.findByOrderIdAndOrderTenantId(order.getId(), tenantId)
                .orElse(OrderPayment.builder()
                        .order(order)
                        .orderId(order.getId())
                        .paymentId(paymentId)
                        .provider(provider)
                        .amount(order.getTotalAmount())
                        .status(status)
                        .build());

        payment.setOrder(order);
        payment.setPaymentId(paymentId);
        payment.setProvider(provider);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(status);
        orderPaymentRepository.save(payment);
    }

    /**
     * Deletes created or failed order when reservation cannot be secured.
     *
     * @param orderId order identifier targeted for rollback cleanup
     * @param tenantId tenant scope for secure ownership validation
     * @return no return; removes unreserved order rows to enforce create invariant
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackUnreservedOrder(UUID orderId, Long tenantId) {
        Order order = findOrder(orderId, tenantId);
        if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.FAILED) {
            orderRepository.delete(order);
        }
    }
}

