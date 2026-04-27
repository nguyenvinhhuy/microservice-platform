package huynv.gatewayservice.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enforces the gateway trust boundary by stripping inbound identity headers and reinjecting trusted values derived
 * from the validated JWT token, while also ensuring correlation and tracing headers are present and propagated.
 */
@Component
public class TrustedRequestContextFilter implements GlobalFilter, Ordered {

    /**
     * Applies trust-boundary header sanitation and injects trusted correlation, tracing, and identity headers.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return resolveJwtAuthentication(exchange)
                .flatMap(authentication -> chain.filter(buildExchangeWithTrustedHeaders(exchange, authentication)))
                .switchIfEmpty(Mono.defer(() -> chain.filter(buildExchangeWithTrustedHeaders(exchange, null))));
    }

    /**
     * Orders the filter to execute early so that downstream filters and rate limiting observe trusted headers only.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return -30;
    }

    /**
     * Builds a mutated exchange that contains trusted correlation, tracing, and identity headers.
     *
     * @param exchange Current web exchange.
     * @param authentication Authentication for the current request, or null when absent.
     * @return Returns a mutated exchange containing sanitized and trusted headers.
     */
    private static ServerWebExchange buildExchangeWithTrustedHeaders(ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        String requestId = ensureRequestId(exchange.getRequest().getHeaders());
        String correlationId = ensureCorrelationId(exchange.getRequest().getHeaders(), requestId);
        String traceparent = ensureTraceparent(exchange.getRequest().getHeaders());
        String traceId = W3cTraceContext.traceIdFromTraceparent(traceparent);

        Jwt jwt = authentication == null ? null : authentication.getToken();
        String userId = jwt == null ? null : resolveUserId(jwt);
        String tenantId = jwt == null ? null : resolveTenantId(jwt);
        String roles = authentication == null ? null : resolveRoles(authentication);

        requestBuilder.headers(headers -> {
            sanitizeUntrustedIdentityHeaders(headers);
            headers.set(GatewayHeaderNames.REQUEST_ID, requestId);
            headers.set(GatewayHeaderNames.CORRELATION_ID, correlationId);
            headers.set(GatewayHeaderNames.TRACEPARENT, traceparent);
            headers.set(GatewayHeaderNames.TRACE_ID, traceId);

            if (userId != null && !userId.isBlank()) {
                headers.set(GatewayHeaderNames.USER_ID, userId);
            }
            if (tenantId != null && !tenantId.isBlank()) {
                headers.set(GatewayHeaderNames.TENANT_ID, tenantId);
            }
            if (roles != null && !roles.isBlank()) {
                headers.set(GatewayHeaderNames.ROLES, roles);
            }
        });

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /**
     * Resolves a JWT authentication token from the request principal or reactive security context when available.
     *
     * @param exchange Current web exchange.
     * @return Returns the JWT authentication token when available, or an empty publisher when absent.
     */
    private static Mono<JwtAuthenticationToken> resolveJwtAuthentication(ServerWebExchange exchange) {
        Mono<JwtAuthenticationToken> fromExchange = exchange.getPrincipal().ofType(JwtAuthenticationToken.class);
        Mono<JwtAuthenticationToken> fromContext = org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .ofType(JwtAuthenticationToken.class);
        return fromExchange.switchIfEmpty(fromContext);
    }

    /**
     * Removes client-provided identity headers that are not trusted across the gateway boundary.
     *
     * @param headers Headers to sanitize before reinjecting trusted identity values.
     * @return Applies side effects by removing identity headers from the provided headers collection.
     */
    private static void sanitizeUntrustedIdentityHeaders(HttpHeaders headers) {
        headers.remove(GatewayHeaderNames.USER_ID);
        headers.remove(GatewayHeaderNames.TENANT_ID);
        headers.remove(GatewayHeaderNames.ROLES);
    }

    /**
     * Ensures a request id is present for correlation, generating one when absent.
     *
     * @param headers Incoming request headers.
     * @return Returns a stable request id for the request.
     */
    private static String ensureRequestId(HttpHeaders headers) {
        String requestId = headers.getFirst(GatewayHeaderNames.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Ensures a correlation id is present for cross-service tracing, falling back to the stable request id.
     *
     * @param headers Incoming request headers.
     * @param requestId Stable request id already resolved for the request.
     * @return Returns the correlation id to propagate downstream.
     */
    private static String ensureCorrelationId(HttpHeaders headers, String requestId) {
        String correlationId = headers.getFirst(GatewayHeaderNames.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            return requestId;
        }
        return correlationId;
    }

    /**
     * Ensures a W3C `traceparent` header is present and valid, generating one when missing or malformed.
     *
     * @param headers Incoming request headers.
     * @return Returns a valid `traceparent` value.
     */
    private static String ensureTraceparent(HttpHeaders headers) {
        String incoming = headers.getFirst(GatewayHeaderNames.TRACEPARENT);
        if (W3cTraceContext.parseTraceId(incoming) != null) {
            return incoming.trim();
        }
        return W3cTraceContext.generateTraceparent();
    }

    /**
     * Extracts a claim value as a string for identity propagation.
     *
     * @param jwt Validated JWT token containing claims.
     * @param claim Claim name to extract.
     * @return Returns the claim value converted to a string, or null when missing.
     */
    private static String claimAsString(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Resolves a user identifier for propagation based on JWT claims and subject fallback.
     *
     * @param jwt Validated JWT token containing identity claims.
     * @return Returns the resolved user identifier, or null when not available.
     */
    private static String resolveUserId(Jwt jwt) {
        String userId = claimAsString(jwt, "userId");
        if (userId == null || userId.isBlank()) {
            return jwt.getSubject();
        }
        return userId;
    }

    /**
     * Resolves a tenant identifier for propagation based on JWT claims.
     *
     * @param jwt Validated JWT token containing tenant claims.
     * @return Returns the resolved tenant identifier, or null when not available.
     */
    private static String resolveTenantId(Jwt jwt) {
        return claimAsString(jwt, "tenantId");
    }

    /**
     * Resolves a comma-separated roles string from Spring Security authorities for downstream propagation.
     *
     * @param authentication Authentication token containing mapped authorities.
     * @return Returns a comma-separated roles string, or an empty string when no authorities are present.
     */
    private static String resolveRoles(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(","));
    }
}
