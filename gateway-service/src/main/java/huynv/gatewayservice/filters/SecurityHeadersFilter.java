package huynv.gatewayservice.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds production-grade security headers to gateway responses to reduce common web attack surfaces.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    /**
     * Writes security response headers at commit time so that downstream handlers cannot remove them.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            addHeaderIfMissing(headers, "X-Content-Type-Options", "nosniff");
            addHeaderIfMissing(headers, "X-Frame-Options", "DENY");
            addHeaderIfMissing(headers, "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");

            String forwardedProto = exchange.getRequest().getHeaders().getFirst(GatewayHeaderNames.X_FORWARDED_PROTO);
            String scheme = exchange.getRequest().getURI().getScheme();
            boolean isHttps = "https".equalsIgnoreCase(forwardedProto) || "https".equalsIgnoreCase(scheme);
            if (isHttps) {
                addHeaderIfMissing(headers, "Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /**
     * Orders the filter late so it can set headers for all responses including error responses.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return 50;
    }

    /**
     * Adds a header only when the response does not already contain the header.
     *
     * @param headers Response headers to mutate.
     * @param name Header name to set.
     * @param value Header value to set.
     * @return Applies side effects by adding the header when missing.
     */
    private static void addHeaderIfMissing(HttpHeaders headers, String name, String value) {
        if (headers.get(name) != null) {
            return;
        }
        headers.add(name, value);
    }
}
