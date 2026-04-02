package huynv.orderviewservice.service;

import huynv.orderviewservice.model.OrderView;
import huynv.orderviewservice.model.OrderViewId;
import huynv.orderviewservice.repository.OrderViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies event-driven updates to the order_view table to keep query state current.
 */
@Service
public class OrderViewProjectionService {

    private final OrderViewRepository orderViewRepository;

    /**
     * Creates a projection service that writes to the order_view table.
     *
     * @param orderViewRepository Repository used to read and write order view rows.
     * @return Initializes a projection service instance.
     */
    public OrderViewProjectionService(OrderViewRepository orderViewRepository) {
        this.orderViewRepository = Objects.requireNonNull(orderViewRepository, "orderViewRepository");
    }

    /**
     * Upserts the base order view fields from order.created events.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param orderId Order identifier updated by the event.
     * @param userId User identifier that owns the order.
     * @param status Order status string.
     * @param totalPrice Total order price.
     * @param createdAt Order creation time.
     * @return Performs a side effect by persisting the updated order view row.
     */
    @Transactional
    public void upsertCreated(Long tenantId, UUID orderId, Long userId, String status, BigDecimal totalPrice, OffsetDateTime createdAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(userId, "userId");

        OrderViewId id = new OrderViewId(tenantId, orderId);
        OrderView view = orderViewRepository.findById(id).orElseGet(OrderView::new);
        view.setId(id);
        view.setUserId(userId);
        view.setStatus(status);
        view.setTotalPrice(totalPrice);
        view.setCreatedAt(createdAt == null ? OffsetDateTime.now() : createdAt);
        view.setUpdatedAt(OffsetDateTime.now());
        orderViewRepository.save(view);
    }

    /**
     * Updates the order status field.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param orderId Order identifier updated by the event.
     * @param status New order status string.
     * @return Performs a side effect by persisting the updated status field.
     */
    @Transactional
    public void updateOrderStatus(Long tenantId, UUID orderId, String status) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");

        OrderViewId id = new OrderViewId(tenantId, orderId);
        OrderView view = orderViewRepository.findById(id).orElseGet(OrderView::new);
        view.setId(id);
        view.setStatus(status);
        view.setUpdatedAt(OffsetDateTime.now());
        orderViewRepository.save(view);
    }

    /**
     * Updates the payment status field.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param orderId Order identifier updated by the event.
     * @param paymentStatus New payment status string.
     * @return Performs a side effect by persisting the updated payment status field.
     */
    @Transactional
    public void updatePaymentStatus(Long tenantId, UUID orderId, String paymentStatus) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");

        OrderViewId id = new OrderViewId(tenantId, orderId);
        OrderView view = orderViewRepository.findById(id).orElseGet(OrderView::new);
        view.setId(id);
        view.setPaymentStatus(paymentStatus);
        view.setUpdatedAt(OffsetDateTime.now());
        orderViewRepository.save(view);
    }

    /**
     * Updates the stock status field.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param orderId Order identifier updated by the event.
     * @param stockStatus New stock status string.
     * @return Performs a side effect by persisting the updated stock status field.
     */
    @Transactional
    public void updateStockStatus(Long tenantId, UUID orderId, String stockStatus) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");

        OrderViewId id = new OrderViewId(tenantId, orderId);
        OrderView view = orderViewRepository.findById(id).orElseGet(OrderView::new);
        view.setId(id);
        view.setStockStatus(stockStatus);
        view.setUpdatedAt(OffsetDateTime.now());
        orderViewRepository.save(view);
    }
}

