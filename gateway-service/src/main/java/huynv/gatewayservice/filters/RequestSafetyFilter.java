package huynv.gatewayservice.filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces request safety limits (maximum request size and overall request timeout) to protect gateway resources.
 */
@Component
public class RequestSafetyFilter implements GlobalFilter, Ordered {

    private final long maxRequestBytes;
    private final Duration requestTimeout;

    /**
     * Creates a request safety filter using configuration-driven safety limits.
     *
     * @param maxRequestBytes Maximum allowed request size in bytes.
     * @param requestTimeout Maximum allowed processing time for a request.
     * @return Returns a constructed RequestSafetyFilter instance.
     */
    public RequestSafetyFilter(
            @Value("${gateway.request.max-bytes:10485760}") long maxRequestBytes,
            @Value("${gateway.request.timeout:10s}") Duration requestTimeout
    ) {
        this.maxRequestBytes = maxRequestBytes;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Rejects requests that exceed size limits and enforces a hard request timeout to prevent resource exhaustion.
     *
     * @param exchange Current web exchange.
     * @param chain Filter chain used to continue request processing.
     * @return Returns a completion signal for the downstream filter chain or an error on violation.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength != null && contentLength > 0 && contentLength > maxRequestBytes) {
            return Mono.error(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request payload too large"));
        }

        AtomicLong observedBytes = new AtomicLong(0);
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            /**
             * Wraps the request body stream to enforce a hard upper bound on bytes read without buffering the full body.
             *
             * @return Returns a body publisher that errors with 413 when the byte limit is exceeded.
             */
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody()
                        .handle((buffer, sink) -> {
                            long next = observedBytes.addAndGet(buffer.readableByteCount());
                            if (next > maxRequestBytes) {
                                DataBufferUtils.release(buffer);
                                sink.error(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request payload too large"));
                                return;
                            }
                            sink.next(buffer);
                        });
            }
        };

        return chain.filter(exchange.mutate().request(decoratedRequest).build())
                .timeout(requestTimeout, Mono.error(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Request timed out")));
    }

    /**
     * Orders the filter early so that oversized requests are rejected before expensive downstream processing.
     *
     * @return Returns the order value for this filter.
     */
    @Override
    public int getOrder() {
        return -25;
    }
}
