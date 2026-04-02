package huynv.orderservice.service;

import huynv.orderservice.context.UserContext;
import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.exception.InvalidOrderStateException;
import huynv.orderservice.saga.OrderSagaCoordinator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Objects;

@Service
public class OrderService {

    private final OrderSagaCoordinator orderSagaCoordinator;
    private final ProductClient productClient;
    private final Counter ordersCreatedTotal;
    private final Counter ordersFailedTotal;

    @Value("${feature.order.enabled:true}")
    private boolean orderFeatureEnabled;

    @Value("${feature.order-product-validation.enabled:true}")
    private boolean productValidationEnabled;

    /**
     * Creates an order service that exposes public API orchestration and publishes platform metrics.
     *
     * @param orderSagaCoordinator Saga coordinator used to persist and execute orchestration steps.
     * @param productClient Product client used for synchronous validation calls.
     * @param meterRegistry Meter registry used to register order API metrics.
     * @return Initializes an order service instance.
     */
    public OrderService(OrderSagaCoordinator orderSagaCoordinator, ProductClient productClient, MeterRegistry meterRegistry) {
        this.orderSagaCoordinator = Objects.requireNonNull(orderSagaCoordinator, "orderSagaCoordinator");
        this.productClient = Objects.requireNonNull(productClient, "productClient");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.ordersCreatedTotal = meterRegistry.counter("orders_created_total", "service", "order-service");
        this.ordersFailedTotal = meterRegistry.counter("orders_failed_total", "service", "order-service");
    }

    /**
     * Creates an order using saga orchestration with idempotency and product validation protections.
     *
     * @param request Validated create-order request payload.
     * @param requestId Mandatory idempotency key from API header.
     * @return Returns a deterministic create response produced by saga orchestration.
     */
    @PreAuthorize("hasRole('USER')")
    public CreateOrderResponse createOrder(CreateOrderRequest request, String requestId) {
        ensureOrderFeatureEnabled("createOrder");
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        if (productValidationEnabled) {
            validateAndNormalizeProductPrices(tenantId, request);
        }
        try {
            CreateOrderResponse response = orderSagaCoordinator.createOrder(tenantId, userId, request, requestId);
            ordersCreatedTotal.increment();
            return response;
        } catch (RuntimeException ex) {
            ordersFailedTotal.increment();
            throw ex;
        }
    }

    /**
     * Delegates pay command execution to persisted saga coordinator boundary.
     *
     * @param orderId Target order identifier from API path.
     * @param request Validated pay request payload.
     * @param requestId Mandatory idempotency key from API header.
     * @return Returns a deterministic action response produced by saga orchestration.
     */
    @PreAuthorize("hasRole('USER')")
    public OrderActionResponse payOrder(UUID orderId, PayOrderRequest request, String requestId) {
        ensureOrderFeatureEnabled("payOrder");
        Long tenantId = requireTenantId();
        return orderSagaCoordinator.payOrder(tenantId, orderId, request, requestId);
    }

    /**
     * Delegates cancel command execution to persisted saga coordinator boundary.
     *
     * @param orderId Target order identifier from API path.
     * @param requestId Mandatory idempotency key from API header.
     * @return Returns a deterministic action response produced by saga orchestration.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OrderActionResponse cancelOrder(UUID orderId, String requestId) {
        ensureOrderFeatureEnabled("cancelOrder");
        Long tenantId = requireTenantId();
        return orderSagaCoordinator.cancelOrder(tenantId, orderId, requestId);
    }

    /**
     * Enforces the operational kill switch for order APIs.
     *
     * @param operation Operation name used for explicit failure messages.
     * @return Throws InvalidOrderStateException when feature.order.enabled is disabled.
     */
    private void ensureOrderFeatureEnabled(String operation) {
        if (!orderFeatureEnabled) {
            throw new InvalidOrderStateException("Order feature disabled operation=" + operation + ".");
        }
    }

    /**
     * Validates products exist and normalizes incoming item prices using product-service as the source of truth.
     *
     * @param tenantId Tenant identifier used for product ownership isolation.
     * @param request Create-order request payload containing item list.
     * @return Updates item price fields to validated product prices or throws on mismatch.
     */
    private void validateAndNormalizeProductPrices(Long tenantId, CreateOrderRequest request) {
        if (request == null || request.getItems() == null) {
            throw new InvalidOrderStateException("Invalid order request payload");
        }
        for (var item : request.getItems()) {
            var product = productClient.getById(tenantId, item.getProductId());
            if (product == null || product.price() == null) {
                throw new InvalidOrderStateException("Product not found productId=" + item.getProductId());
            }
            if (request.getCurrency() != null && product.currency() != null && !request.getCurrency().equalsIgnoreCase(product.currency())) {
                throw new InvalidOrderStateException("Currency mismatch productId=" + item.getProductId());
            }
            BigDecimal price = product.price();
            if (price.signum() <= 0) {
                throw new InvalidOrderStateException("Invalid product price productId=" + item.getProductId());
            }
            item.setPrice(price);
        }
    }

    /**
     * Enforces tenant context presence to fail closed when gateway identity is missing.
     *
     * @return Returns the tenant id from trusted request context.
     */
    private Long requireTenantId() {
        Long tenantId = UserContext.getTenantId();
        if (tenantId == null) {
            throw new InvalidOrderStateException("Missing tenant context");
        }
        return tenantId;
    }

    /**
     * Enforces user context presence to fail closed on malformed identity propagation.
     *
     * @return Returns the user id from trusted request context.
     */
    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new InvalidOrderStateException("Missing user context");
        }
        return userId;
    }
}
