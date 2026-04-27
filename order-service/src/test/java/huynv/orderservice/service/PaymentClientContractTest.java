package huynv.orderservice.service;

import huynv.orderservice.config.PaymentClientProperties;
import huynv.orderservice.dto.PaymentProcessRequest;
import huynv.orderservice.dto.PaymentResponse;
import huynv.orderservice.resilience.ResilienceExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentClientContractTest {

    @Mock
    private WebClient paymentWebClient;

    @Mock
    private PaymentClientProperties properties;

    @Mock
    private ResilienceExecutor resilienceExecutor;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private PaymentClient paymentClient;

    @Test
    void shouldSendIdempotencyOnlyInHeader() {
        PaymentResponse expected = new PaymentResponse(UUID.randomUUID(), UUID.randomUUID(), "SUCCEEDED", "simulated", null, "idem-1");
        when(resilienceExecutor.execute(any(), any())).thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponse>>getArgument(1).get());
        when(properties.getProcessPath()).thenReturn("/api/payments");
        when(paymentWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/payments")).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(any(), any())).thenReturn(requestBodyUriSpec);
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any(PaymentProcessRequest.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(PaymentResponse.class)).thenReturn(Mono.just(expected));

        paymentClient.charge(expected.orderId(), 10L, BigDecimal.TEN, "USD", "simulated", "idem-1", "corr-1", "req-1");

        ArgumentCaptor<PaymentProcessRequest> bodyCaptor = ArgumentCaptor.forClass(PaymentProcessRequest.class);
        verify(requestBodyUriSpec).header("Idempotency-Key", "idem-1");
        verify(requestBodyUriSpec).header("X-Request-Id", "req-1");
        verify(requestBodyUriSpec).bodyValue(bodyCaptor.capture());
        assertEquals(expected.orderId(), bodyCaptor.getValue().orderId());
        assertEquals("corr-1", bodyCaptor.getValue().correlationId());
        assertEquals(7, bodyCaptor.getValue().getClass().getRecordComponents().length);
    }
}



