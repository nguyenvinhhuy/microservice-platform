package huynv.gatewayservice.filters;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Exposes production-grade gateway RED metrics (requests, errors, latency) with low-cardinality tags.
 */
@Component
public class GatewayMetricsFilter implements GlobalFilter, Ordered {

    private final MeterRegistry meterRegistry;

    /**
     * Creates a gateway metrics filter that records Prometheus-friendly counters and timers.
     *
     * @param meterRegistry Micrometer meter registry used for metrics recording.
     * @return Returns a constructed GatewayMetricsFilter instance.
     */
    public GatewayMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records request count, error count, and latency metrics tagged by route id and status class.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        return chain.filter(exchange)
                .doFinally(signalType -> record(exchange, startNanos));
    }

    /**
     * Orders the filter late to capture the final response status and resolved route id.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * Records request metrics using low-cardinality tags derived from the matched route and final status code.
     *
     * @param exchange Current web exchange.
     * @param startNanos Start time in nanoseconds used to compute latency.
     * @return Applies side effects by recording Micrometer counters and timers.
     */
    private void record(ServerWebExchange exchange, long startNanos) {
        String routeId = resolveRouteId(exchange);
        int statusCode = exchange.getResponse().getStatusCode() == null
                ? 0
                : exchange.getResponse().getStatusCode().value();
        String statusClass = statusClass(statusCode);

        Counter.builder("gateway_requests_total")
                .tag("routeId", routeId)
                .tag("statusClass", statusClass)
                .register(meterRegistry)
                .increment();

        if (statusCode >= 500 || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
            Counter.builder("gateway_errors_total")
                    .tag("routeId", routeId)
                    .tag("statusClass", statusClass)
                    .register(meterRegistry)
                    .increment();
        }

        Timer.builder("gateway_request_latency_seconds")
                .tag("routeId", routeId)
                .tag("statusClass", statusClass)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Resolves the route identifier for tagging metrics, falling back to "unknown" when unmatched.
     *
     * @param exchange Current web exchange.
     * @return Returns the matched gateway route id or "unknown" when unavailable.
     */
    private static String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "unknown" : route.getId();
    }

    /**
     * Converts a numeric HTTP status code into a stable status class label.
     *
     * @param statusCode HTTP response status code.
     * @return Returns a status class label such as "2xx" or "5xx".
     */
    private static String statusClass(int statusCode) {
        if (statusCode >= 100 && statusCode < 200) {
            return "1xx";
        }
        if (statusCode >= 200 && statusCode < 300) {
            return "2xx";
        }
        if (statusCode >= 300 && statusCode < 400) {
            return "3xx";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "4xx";
        }
        if (statusCode >= 500 && statusCode < 600) {
            return "5xx";
        }
        return "unknown";
    }
}
