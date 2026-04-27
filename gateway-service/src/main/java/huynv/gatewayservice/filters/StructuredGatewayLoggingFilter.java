package huynv.gatewayservice.filters;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

/**
 * Emits structured JSON request logs for gateway traffic with correlation, tracing, and identity fields.
 */
@Component
public class StructuredGatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(StructuredGatewayLoggingFilter.class);

    /**
     * Logs a single structured event at request completion that includes identity, trace, route, status, and latency.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        return resolveAuthentication(exchange)
                .flatMap(authentication -> chain.filter(exchange).doFinally(signal -> logExchange(exchange, authentication, startNanos)))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).doFinally(signal -> logExchange(exchange, null, startNanos))));
    }

    /**
     * Orders the logging filter last so it records final route selection and final status code.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * Resolves the route identifier for logging, falling back to "unknown" when unmatched.
     *
     * @param exchange Current web exchange.
     * @return Returns the matched gateway route id or "unknown" when unavailable.
     */
    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unknown" : route.getId();
    }

    /**
     * Converts a numeric HTTP status code into a stable status class label for low-cardinality logging and metrics.
     *
     * @param status HTTP response status code.
     * @return Returns a status class label such as "2xx" or "5xx".
     */
    private static String statusClass(Integer status) {
        if (status == null) {
            return "unknown";
        }
        if (status >= 100 && status < 200) {
            return "1xx";
        }
        if (status >= 200 && status < 300) {
            return "2xx";
        }
        if (status >= 300 && status < 400) {
            return "3xx";
        }
        if (status >= 400 && status < 500) {
            return "4xx";
        }
        if (status >= 500 && status < 600) {
            return "5xx";
        }
        return "unknown";
    }

    /**
     * Writes an MDC key only when the provided value is non-null and non-blank.
     *
     * @param key MDC key name.
     * @param value MDC value to set.
     * @return Applies side effects by setting the MDC key/value pair when the value is present.
     */
    private static void putMdcIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
    }

    /**
     * Logs a structured gateway request event using the final response status and resolved route metadata.
     *
     * @param exchange Current web exchange.
     * @param authentication Authentication for the current request, or null when unavailable.
     * @param startNanos Start time in nanoseconds used to compute latency.
     * @return Applies side effects by emitting a structured log event.
     */
    private static void logExchange(ServerWebExchange exchange, Authentication authentication, long startNanos) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String requestId = headers.getFirst(GatewayHeaderNames.REQUEST_ID);
        String correlationId = headers.getFirst(GatewayHeaderNames.CORRELATION_ID);
        String traceparent = headers.getFirst(GatewayHeaderNames.TRACEPARENT);
        String traceId = W3cTraceContext.parseTraceId(traceparent) == null ? null : W3cTraceContext.traceIdFromTraceparent(traceparent);

        Identity identity = Identity.from(authentication);
        String routeId = resolveRouteId(exchange);
        Integer status = exchange.getResponse().getStatusCode() == null ? null : exchange.getResponse().getStatusCode().value();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;

        putMdcIfPresent("requestId", requestId);
        putMdcIfPresent("correlationId", correlationId);
        putMdcIfPresent("traceId", traceId);
        putMdcIfPresent("tenantId", identity.tenantId());
        putMdcIfPresent("userId", identity.userId());
        putMdcIfPresent("routeId", routeId);
        putMdcIfPresent("status", status == null ? null : String.valueOf(status));
        putMdcIfPresent("statusClass", statusClass(status));
        putMdcIfPresent("latencyMs", String.valueOf(latencyMs));
        try {
            log.info("gateway_request",
                    StructuredArguments.kv("method", exchange.getRequest().getMethod() == null ? null : exchange.getRequest().getMethod().name()),
                    StructuredArguments.kv("path", exchange.getRequest().getURI().getPath())
            );
        } finally {
            MDC.clear();
        }
    }

    private record Identity(String userId, String tenantId) {
        /**
         * Extracts user and tenant identity from a JWT-based authentication token for structured logging.
         *
         * @param authentication Authentication token for the current request.
         * @return Returns an identity snapshot with userId and tenantId, or null values when unavailable.
         */
        private static Identity from(Authentication authentication) {
            Jwt jwt = extractJwt(authentication);
            if (jwt == null) {
                return new Identity(null, null);
            }
            String userId = claimAsString(jwt, "userId");
            if (userId == null || userId.isBlank()) {
                userId = jwt.getSubject();
            }
            String tenantId = claimAsString(jwt, "tenantId");
            return new Identity(userId, tenantId);
        }

        /**
         * Extracts a claim value as a string for logging.
         *
         * @param jwt Validated JWT token containing claims.
         * @param claim Claim name to extract.
         * @return Returns the claim value converted to a string, or null when missing.
         */
        private static String claimAsString(Jwt jwt, String claim) {
            Object value = jwt.getClaims().get(claim);
            return value == null ? null : String.valueOf(value);
        }
    }

    /**
     * Resolves a trusted authentication token from the reactive security context, with a best-effort fallback.
     *
     * @param exchange Current web exchange.
     * @return Returns the authenticated security token when available, or an empty publisher when absent.
     */
    private static Mono<Authentication> resolveAuthentication(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .switchIfEmpty(exchange.getPrincipal().ofType(Authentication.class));
    }

    /**
     * Extracts a validated JWT token from a Spring Security authentication object when available.
     *
     * @param authentication Authentication token for the current request.
     * @return Returns the validated JWT token, or null when the authentication does not expose a JWT.
     */
    private static Jwt extractJwt(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
