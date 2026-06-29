package huynv.userservice.config;

import huynv.userservice.exception.BadRequestException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the Idempotency-Key contract on replay-sensitive user-service write endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(prefix = "user-service.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyFilter extends OncePerRequestFilter {

    /**
     * Validates the Idempotency-Key header for supported write endpoints before request handling proceeds.
     *
     * @param request Current HTTP request.
     * @param response Current HTTP response.
     * @param filterChain Remaining filter chain.
     * @return Performs a side effect by rejecting unsupported requests that omit Idempotency-Key.
     * @throws ServletException Throws when downstream request processing fails at the servlet layer.
     * @throws IOException Throws when downstream request processing fails at the I/O layer.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (requiresIdempotencyKey(request)) {
            String idempotencyKey = request.getHeader("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "The Idempotency-Key header is required for this write endpoint.");
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Determines whether the current request path and method require replay protection.
     *
     * @param request Current HTTP request.
     * @return Returns true when the request targets a replay-sensitive endpoint.
     */
    private boolean requiresIdempotencyKey(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        return ("PUT".equalsIgnoreCase(method) && "/users/me".equals(path))
                || ("PUT".equalsIgnoreCase(method) && "/users/preferences".equals(path))
                || ("POST".equalsIgnoreCase(method) && "/users/addresses".equals(path));
    }
}

