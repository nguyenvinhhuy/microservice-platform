package huynv.orderservice.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Injects OpenTelemetry trace context and correlation identifiers into outbound HTTP requests.
 */
public final class OtelPropagationExchangeFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private OtelPropagationExchangeFilter() {
    }

    /**
     * Creates an exchange filter that injects W3C trace context headers for distributed tracing.
     *
     * @return Returns an ExchangeFilterFunction that mutates request headers before execution.
     */
    public static ExchangeFilterFunction create() {
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
        return (request, next) -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            builder.headers(headers -> propagator.inject(Context.current(), headers, (h, key, value) -> h.set(key, value)));

            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                builder.header(CORRELATION_ID_HEADER, correlationId);
            }

            return next.exchange(builder.build());
        };
    }
}
