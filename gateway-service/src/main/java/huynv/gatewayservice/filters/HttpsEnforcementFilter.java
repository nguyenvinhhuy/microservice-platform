package huynv.gatewayservice.filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces HTTPS usage for externally-facing requests when enabled, based on forwarded protocol headers.
 */
@Component
public class HttpsEnforcementFilter implements GlobalFilter, Ordered {

    private final boolean requireHttps;

    /**
     * Creates an HTTPS enforcement filter controlled by configuration.
     *
     * @param requireHttps Flag to require HTTPS for requests.
     * @return Returns a constructed HttpsEnforcementFilter instance.
     */
    public HttpsEnforcementFilter(@Value("${gateway.security.require-https:false}") boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    /**
     * Rejects non-HTTPS requests when HTTPS enforcement is enabled.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream chain or an error when HTTPS is required.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!requireHttps) {
            return chain.filter(exchange);
        }

        String forwardedProto = exchange.getRequest().getHeaders().getFirst(GatewayHeaderNames.X_FORWARDED_PROTO);
        String scheme = exchange.getRequest().getURI().getScheme();
        boolean isHttps = "https".equalsIgnoreCase(forwardedProto) || "https".equalsIgnoreCase(scheme);
        if (!isHttps) {
            return Mono.error(new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED, "HTTPS is required"));
        }

        return chain.filter(exchange);
    }

    /**
     * Orders the filter very early to avoid processing requests that are not allowed under HTTPS enforcement.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return -40;
    }
}

