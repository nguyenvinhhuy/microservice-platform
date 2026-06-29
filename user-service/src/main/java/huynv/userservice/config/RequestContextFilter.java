package huynv.userservice.config;

import huynv.userservice.metrics.UserMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Enriches MDC with request-scoped metadata and records per-request metrics for user-service APIs.
 */
@Component("userRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    private final UserMetrics userMetrics;

    /**
     * Creates a request filter that populates MDC and records request metrics.
     *
     * @param userMetrics Metrics recorder used for request counters and latency timers.
     * @return Initializes a request context filter instance.
     */
    public RequestContextFilter(UserMetrics userMetrics) {
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
    }

    /**
     * Populates MDC values before request handling and clears them after the response is committed.
     *
     * @param request Current HTTP request.
     * @param response Current HTTP response.
     * @param filterChain Remaining filter chain.
     * @return Performs side effects by updating MDC and recording request metrics.
     * @throws ServletException Throws when downstream request processing fails at the servlet layer.
     * @throws IOException Throws when downstream request processing fails at the I/O layer.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String route = request.getRequestURI();
        String requestId = firstNonBlank(sanitizeHeader(request.getHeader("X-Request-Id")), UUID.randomUUID().toString());
        String traceId = currentTraceId();
        String correlationId = firstNonBlank(sanitizeHeader(request.getHeader("X-Correlation-Id")), requestId);
        String causationId = firstNonBlank(sanitizeHeader(request.getHeader("X-Causation-Id")), correlationId);

        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);
        MDC.put("route", route);
        MDC.put("correlationId", correlationId);
        MDC.put("causationId", causationId);
        putIfPresent("traceparent", sanitizeHeader(request.getHeader("traceparent")));
        putIfPresent("tracestate", sanitizeHeader(request.getHeader("tracestate")));
        putJwtContext(request);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            MDC.put("latencyMs", String.valueOf(latencyMs));
            userMetrics.recordRequest(route, response.getStatus(), Duration.ofMillis(latencyMs));
            MDC.clear();
        }
    }

    /**
     * Extracts tenant and user identifiers from the authenticated JWT when available.
     *
     * @param request Current HTTP request.
     * @return Performs side effects by populating tenantId and userId MDC fields when claims are present.
     */
    private void putJwtContext(HttpServletRequest request) {
        Object principal = request.getUserPrincipal();
        if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken authenticationToken
                && authenticationToken.getPrincipal() instanceof Jwt jwt) {
            putIfPresent("tenantId", firstNonBlank(jwt.getClaimAsString("tenantId"), jwt.getClaimAsString("tenant_id")));
            putIfPresent("userId", jwt.getClaimAsString("sub"));
        }
    }

    /**
     * Stores a non-blank value in MDC under the provided key.
     *
     * @param key MDC key name to populate.
     * @param value Candidate value to store.
     * @return Performs a side effect by writing to MDC when the value is non-blank.
     */
    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    /**
     * Resolves the current OpenTelemetry trace identifier when a span is active.
     *
     * @return Returns the active trace identifier or a generated fallback when no span is active.
     */
    private String currentTraceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            return spanContext.getTraceId();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the first non-blank value from the provided candidates.
     *
     * @param candidates Candidate values ordered by preference.
     * @return Returns the first non-blank candidate, or null when none are available.
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Sanitizes a client-supplied header value before it is written to MDC or propagated downstream.
     *
     * @param value Candidate header value supplied by the caller.
     * @return Returns a trimmed single-line header value, or null when the value is blank.
     */
    private String sanitizeHeader(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", "").replace("\n", "").trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }
}

