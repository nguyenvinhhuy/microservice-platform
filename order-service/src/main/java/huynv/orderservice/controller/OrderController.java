package huynv.orderservice.controller;

import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.service.OrderService;
import huynv.orderservice.web.CommandIdempotencyKeyResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes mutating order endpoints that require command-level idempotency keys.
 */
@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order controller that delegates orchestration to the application service.
     *
     * @param orderService The service that executes order command workflows.
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order by using the provided command idempotency headers.
     *
     * @param idempotencyKeyHeader The preferred command idempotency header value.
     * @param requestIdHeader The legacy request identifier header used as a temporary fallback.
     * @param request The validated create-order request payload.
     * @return Returns a created response entity containing the persisted order summary.
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader(value = CommandIdempotencyKeyResolver.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKeyHeader,
            @RequestHeader(value = CommandIdempotencyKeyResolver.REQUEST_ID_HEADER, required = false) String requestIdHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        String idempotencyKey = CommandIdempotencyKeyResolver.require(idempotencyKeyHeader, requestIdHeader);
        CreateOrderResponse response = orderService.createOrder(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Starts the payment workflow for a single order by using the provided command idempotency headers.
     *
     * @param orderId The order identifier targeted by the pay command.
     * @param idempotencyKeyHeader The preferred command idempotency header value.
     * @param requestIdHeader The legacy request identifier header used as a temporary fallback.
     * @param request The validated payment command payload.
     * @return Returns a response entity containing the current order action result.
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderActionResponse> payOrder(
            @PathVariable UUID orderId,
            @RequestHeader(value = CommandIdempotencyKeyResolver.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKeyHeader,
            @RequestHeader(value = CommandIdempotencyKeyResolver.REQUEST_ID_HEADER, required = false) String requestIdHeader,
            @Valid @RequestBody PayOrderRequest request
    ) {
        String idempotencyKey = CommandIdempotencyKeyResolver.require(idempotencyKeyHeader, requestIdHeader);
        OrderActionResponse response = orderService.payOrder(orderId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an existing order and releases inventory reservations when required.
     *
     * @param orderId The order identifier targeted by the cancel command.
     * @param idempotencyKeyHeader The preferred command idempotency header value.
     * @param requestIdHeader The legacy request identifier header used as a temporary fallback.
     * @return Returns a response entity containing the cancellation result.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderActionResponse> cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader(value = CommandIdempotencyKeyResolver.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKeyHeader,
            @RequestHeader(value = CommandIdempotencyKeyResolver.REQUEST_ID_HEADER, required = false) String requestIdHeader
    ) {
        String idempotencyKey = CommandIdempotencyKeyResolver.require(idempotencyKeyHeader, requestIdHeader);
        OrderActionResponse response = orderService.cancelOrder(orderId, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
