package huynv.paymentservice.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Copies the current OpenTelemetry span identifiers into MDC for structured logging.
 */
@Component
@Order(15)
public class OtelMdcServletFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Adds trace identifiers to MDC for the duration of the request and restores prior values after completion.
     *
     * @param request Incoming servlet request.
     * @param response Outgoing servlet response.
     * @param filterChain Filter chain used to continue request processing.
     * @return No return; mutates MDC for log correlation and then restores prior values.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousTraceId = MDC.get("traceId");
        String previousSpanId = MDC.get("spanId");
        String previousRequestId = MDC.get("requestId");
        String previousCorrelationId = MDC.get("correlationId");
        try {
            SpanContext spanContext = Span.current().getSpanContext();
            if (spanContext.isValid()) {
                MDC.put("traceId", spanContext.getTraceId());
                MDC.put("spanId", spanContext.getSpanId());
            }
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId != null && !requestId.isBlank()) {
                MDC.put("requestId", requestId);
            }
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if ((correlationId == null || correlationId.isBlank()) && requestId != null && !requestId.isBlank()) {
                correlationId = requestId;
            }
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc("traceId", previousTraceId);
            restoreMdc("spanId", previousSpanId);
            restoreMdc("requestId", previousRequestId);
            restoreMdc("correlationId", previousCorrelationId);
        }
    }

    /**
     * Restores a single MDC key to a previous value or removes it when absent.
     *
     * @param key MDC key name to restore.
     * @param previousValue Previous value to restore, or null when the key should be removed.
     * @return No return; updates MDC as a side effect.
     */
    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
