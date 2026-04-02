package huynv.gatewayservice.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Declares gateway rate limiting key resolution using tenant, user, and routed endpoint identity when available.
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Resolves a stable rate limiting key based on trusted JWT claims and route identity, falling back to client ip.
     *
     * @return Returns a KeyResolver used by Spring Cloud Gateway request rate limiting filters.
     */
    @Bean
    public KeyResolver tenantUserRouteKeyResolver() {
        return exchange -> resolveJwtAuthentication(exchange)
                .map(authentication -> buildKey(exchange, authentication))
                .switchIfEmpty(Mono.fromSupplier(() -> buildKey(exchange, null)));
    }

    /**
     * Builds a rate limiting key that supports per-tenant, per-user, and per-endpoint policies using trusted claims.
     *
     * @param exchange Current web exchange used to read request headers and resolved route metadata.
     * @param authentication JWT authentication context for the current request, when present.
     * @return Returns a stable key that can be used as a Redis rate limit bucket identifier.
     */
    private static String buildKey(ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        String tenant = null;
        String user = null;
        Jwt jwt = authentication == null ? null : authentication.getToken();
        if (jwt != null) {
            Object tenantClaim = jwt.getClaims().get("tenantId");
            Object userClaim = jwt.getClaims().get("userId");
            if (tenantClaim != null) {
                tenant = String.valueOf(tenantClaim);
            }
            if (userClaim != null) {
                user = String.valueOf(userClaim);
            }
            if (user == null || user.isBlank()) {
                user = jwt.getSubject();
            }
        }

        String routeId = resolveRouteId(exchange);
        if (tenant != null && !tenant.isBlank() && user != null && !user.isBlank()) {
            return "tenant:" + tenant + ":user:" + user + ":route:" + routeId;
        }
        if (tenant != null && !tenant.isBlank()) {
            return "tenant:" + tenant + ":route:" + routeId;
        }
        if (user != null && !user.isBlank()) {
            return "user:" + user + ":route:" + routeId;
        }

        String ip = resolveClientIp(exchange);
        return "ip:" + ip + ":route:" + routeId;
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
     * Resolves the route id (endpoint identity) for a request when Spring Cloud Gateway has matched a route.
     *
     * @param exchange Current web exchange.
     * @return Returns the resolved route id, or a stable fallback when no route is available.
     */
    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unknown" : route.getId();
    }

    /**
     * Resolves a client ip address using forwarded headers where available.
     *
     * @param exchange Current web exchange.
     * @return Returns the client ip address or the string "unknown" when it cannot be determined.
     */
    private static String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma < 0 ? forwardedFor.trim() : forwardedFor.substring(0, comma).trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : String.valueOf(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }
}
