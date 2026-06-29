package huynv.fileservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates request-scoped MDC keys and guarantees cleanup after request processing completes.
 */
@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    /**
     * Populates the requestId MDC key for the current request and clears MDC state afterwards.
     *
     * @param request Current HTTP request.
     * @param response Current HTTP response.
     * @param filterChain Remaining filter chain.
     * @return Performs side effects by populating and clearing MDC state.
     * @throws ServletException Throws when the servlet container reports a filter error.
     * @throws IOException Throws when I/O fails during filter processing.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}

