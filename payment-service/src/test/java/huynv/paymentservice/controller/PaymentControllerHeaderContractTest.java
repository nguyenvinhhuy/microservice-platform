package huynv.paymentservice.controller;

import huynv.paymentservice.dto.PaymentProcessRequest;
import huynv.paymentservice.dto.PaymentResponse;
import huynv.paymentservice.repository.PaymentRepository;
import huynv.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerHeaderContractTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(paymentService, paymentRepository);
    }

    @Test
    void shouldRequireIdempotencyKeyHeader() {
        PaymentProcessRequest request = baseRequest();
        when(paymentService.processPayment(any(), any())).thenReturn(new PaymentResponse(UUID.randomUUID(), request.orderId(), null, "simulated", null, "idem-1"));

        paymentController.process("idem-1", request);

        ArgumentCaptor<PaymentProcessRequest> captor = ArgumentCaptor.forClass(PaymentProcessRequest.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentService).processPayment(captor.capture(), keyCaptor.capture());
        assertEquals("idem-1", keyCaptor.getValue());
        assertEquals(request.orderId(), captor.getValue().orderId());
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        assertThrows(RuntimeException.class, () -> paymentController.process(null, baseRequest()));
    }

    private PaymentProcessRequest baseRequest() {
        return new PaymentProcessRequest(
                UUID.randomUUID(),
                10L,
                BigDecimal.TEN,
                "USD",
                "simulated",
                "corr-1",
                "trace-1"
        );
    }
}

