package huynv.orderservice.ratelimit;

import huynv.orderservice.context.UserContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates fail-open behavior for order rate limiting when Redis is unavailable.
 */
class OrderCreateRateLimitFilterTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * Ensures Redis errors do not block order creation requests.
     *
     * @return No return; asserts the filter allows the request to proceed.
     */
    @Test
    void shouldFailOpenWhenRedisThrows() throws Exception {
        UserContext.setTenantId(1L);
        UserContext.setUserId(2L);

        OrderRateLimitingProperties properties = new OrderRateLimitingProperties();
        properties.setEnabled(true);
        properties.setCapacity(1);
        properties.setRefillTokens(1);
        properties.setRefillPeriod(java.time.Duration.ofSeconds(1));

        RedisTokenBucketRateLimiter rateLimiter = Mockito.mock(RedisTokenBucketRateLimiter.class);
        Mockito.when(rateLimiter.tryConsumeOne(Mockito.anyString())).thenThrow(new RuntimeException("redis down"));

        OrderCreateRateLimitFilter filter = new OrderCreateRateLimitFilter(properties, rateLimiter, new SimpleMeterRegistry());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
                ((MockHttpServletResponse) response).setStatus(201);
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals(201, response.getStatus());
    }
}
