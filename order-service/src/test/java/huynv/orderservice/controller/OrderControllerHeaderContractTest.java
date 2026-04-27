package huynv.orderservice.controller;

import huynv.orderservice.dto.CreateOrderRequest;
import huynv.orderservice.dto.CreateOrderResponse;
import huynv.orderservice.dto.OrderActionResponse;
import huynv.orderservice.dto.PayOrderRequest;
import huynv.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerHeaderContractTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void shouldPreferIdempotencyKeyForCreate() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCurrency("USD");
        request.setItems(List.of());
        when(orderService.createOrder(any(), any())).thenReturn(CreateOrderResponse.builder().status("CREATED").build());

        orderController.createOrder("idem-1", "req-1", request);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(orderService).createOrder(any(), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("idem-1", captor.getValue());
    }

    @Test
    void shouldFallbackToRequestIdForPay() {
        PayOrderRequest request = new PayOrderRequest();
        request.setProvider("simulated");
        when(orderService.payOrder(any(), any(), any())).thenReturn(OrderActionResponse.builder().status("PROCESSING").build());

        orderController.payOrder(UUID.randomUUID(), null, "req-1", request);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(orderService).payOrder(any(), any(), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("req-1", captor.getValue());
    }

    @Test
    void shouldRejectMissingCommandKeyForCancel() {
        assertThrows(IllegalArgumentException.class, () -> orderController.cancelOrder(UUID.randomUUID(), null, null));
    }
}

