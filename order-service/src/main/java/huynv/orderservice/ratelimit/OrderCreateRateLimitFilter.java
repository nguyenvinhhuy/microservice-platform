package huynv.orderservice.ratelimit;

import huynv.orderservice.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;

/**
 * Applies a Redis-backed token bucket limiter to the public order creation endpoint.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = false)
public class OrderCreateRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OrderCreateRateLimitFilter.class);

    private final OrderRateLimitingProperties properties;
    private final RedisTokenBucketRateLimiter rateLimiter;
    private final Counter redisErrorsTotal;

    /**
     * Creates a filter that enforces token bucket rate limiting for POST /orders.
     *
     * @param properties Rate limiting configuration properties.
     * @param rateLimiter Redis rate limiter used to enforce token bucket semantics.
     * @param meterRegistry Meter registry used to record Redis outage and error metrics.
     * @return Initializes an order create rate limiting filter.
     */
    public OrderCreateRateLimitFilter(OrderRateLimitingProperties properties, RedisTokenBucketRateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.redisErrorsTotal = meterRegistry.counter("rate_limit_redis_errors_total", "service", "order-service");
    }

    /**
     * Limits filtering to the create-order endpoint.
     *
     * @param request Incoming servlet request used to determine whether rate limiting applies.
     * @return Returns true when the request should not be rate limited.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/orders".equals(request.getRequestURI());
    }

    /**
     * Enforces token bucket rate limiting and returns HTTP 429 when the bucket is empty.
     *
     * @param request Incoming servlet request to rate limit.
     * @param response Servlet response used to send HTTP 429 when blocked.
     * @param filterChain Filter chain used to continue request processing.
     * @return Allows or blocks the request by writing an HTTP response.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long tenantId = UserContext.getTenantId();
        Long userId = UserContext.getUserId();
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = "rate_limit:orders:" + tenantId + ":" + userId + ":" + ip;

        boolean allowed;
        try {
            allowed = rateLimiter.tryConsumeOne(key);
        } catch (RuntimeException ex) {
            redisErrorsTotal.increment();
            log.warn("Rate limiter Redis error. key={} error={}", key, ex.getMessage());
            allowed = true;
        }
        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Rate limit exceeded.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
