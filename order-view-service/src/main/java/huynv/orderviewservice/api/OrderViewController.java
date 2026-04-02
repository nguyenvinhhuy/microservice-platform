package huynv.orderviewservice.api;

import huynv.orderviewservice.model.OrderView;
import huynv.orderviewservice.model.OrderViewId;
import huynv.orderviewservice.repository.OrderViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Exposes read-only query APIs backed by the order_view table.
 */
@RestController
public class OrderViewController {

    private final OrderViewRepository orderViewRepository;

    /**
     * Creates a read-only controller for order view queries.
     *
     * @param orderViewRepository Repository used to query order view rows.
     * @return Initializes an order view controller instance.
     */
    public OrderViewController(OrderViewRepository orderViewRepository) {
        this.orderViewRepository = Objects.requireNonNull(orderViewRepository, "orderViewRepository");
    }

    /**
     * Lists order views for the current tenant and optionally the current user.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param userId Optional user identifier to scope queries to the current user.
     * @param page Page index starting at 0.
     * @param size Page size.
     * @return Returns a page of order view responses.
     */
    @GetMapping("/orders")
    public Page<OrderViewResponse> listOrders(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(name = "X-User-Id", required = false) Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        Page<OrderView> result;
        if (userId != null) {
            result = orderViewRepository.findByIdTenantIdAndUserId(tenantId, userId, PageRequest.of(page, size));
        } else {
            result = orderViewRepository.findByIdTenantId(tenantId, PageRequest.of(page, size));
        }
        return result.map(OrderViewController::toResponse);
    }

    /**
     * Loads a single order view row for the current tenant.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param orderId Order identifier to load.
     * @return Returns the order view response.
     */
    @GetMapping("/orders/{id}")
    public OrderViewResponse getById(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("id") UUID orderId
    ) {
        OrderView view = orderViewRepository.findById(new OrderViewId(tenantId, orderId)).orElseThrow();
        return toResponse(view);
    }

    private static OrderViewResponse toResponse(OrderView view) {
        return new OrderViewResponse(
                view.getId().getOrderId(),
                view.getUserId(),
                view.getStatus(),
                view.getPaymentStatus(),
                view.getStockStatus(),
                view.getTotalPrice(),
                view.getCreatedAt()
        );
    }
}

