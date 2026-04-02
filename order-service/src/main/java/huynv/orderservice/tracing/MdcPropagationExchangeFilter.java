package huynv.orderservice.tracing;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.util.Objects;

/**
 * Propagates correlation and trace identifiers from MDC into outbound HTTP headers.
 */
public final class MdcPropagationExchangeFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private MdcPropagationExchangeFilter() {
    }

    /**
     * Creates an ExchangeFilterFunction that adds MDC identifiers to outbound requests when missing.
     *
     * @return Returns an ExchangeFilterFunction suitable for WebClient builders.
     */
    public static ExchangeFilterFunction create() {
        return (request, next) -> next.exchange(withMdcHeaders(request));
    }

    /**
     * Copies the request and appends missing identifiers based on MDC fields.
     *
     * @param request Outbound request to enrich.
     * @return Returns a new ClientRequest containing propagated identifier headers.
     */
    private static ClientRequest withMdcHeaders(ClientRequest request) {
        Objects.requireNonNull(request, "request");
        ClientRequest.Builder builder = ClientRequest.from(request);
        addIfMissing(builder, request, REQUEST_ID_HEADER, MDC.get("requestId"));
        addIfMissing(builder, request, TRACE_ID_HEADER, MDC.get("traceId"));
        addIfMissing(builder, request, CORRELATION_ID_HEADER, MDC.get("correlationId"));
        return builder.build();
    }

    /**
     * Adds a header value only when it is missing and a non-blank candidate is available.
     *
     * @param builder Client request builder to mutate.
     * @param request Request used to detect existing headers.
     * @param headerName Header name to set when missing.
     * @param value Candidate header value to use.
     * @return Adds the header as a side effect on the builder.
     */
    private static void addIfMissing(ClientRequest.Builder builder, ClientRequest request, String headerName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (request.headers().get(headerName) != null) {
            return;
        }
        builder.header(headerName, value);
    }
}
