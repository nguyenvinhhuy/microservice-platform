package huynv.orderservice.controller;

import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    /**
     * OrderController operation.
     *
     * @param orderService input parameter
     * @return performs side effects defined by this operation
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Public API to create a new order command with mandatory idempotency key.
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResponse response = orderService.createOrder(request, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Public API to trigger payment confirmation flow for one order.
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderActionResponse> payOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody PayOrderRequest request
    ) {
        OrderActionResponse response = orderService.payOrder(orderId, request, requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * Public API to cancel an existing order and release reservation when required.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderActionResponse> cancelOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId
    ) {
        OrderActionResponse response = orderService.cancelOrder(orderId, requestId);
        return ResponseEntity.ok(response);
    }
}
