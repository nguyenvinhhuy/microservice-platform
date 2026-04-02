package huynv.inventoryservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A filter that extracts tenantId and userId from request headers and sets them in the UserContext.
 * This ensures that all subsequent operations within the request scope are tenant-aware.
 */
@Component
@Slf4j
public class UserContextFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * Extracts tenant/user headers, sets request context, and clears context after completion.
     *
     * @param request incoming servlet request with tenant and user headers
     * @param response servlet response passed through to downstream filters
     * @param filterChain chain used to continue request processing
     * @return performs side effects defined by this operation
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantIdHeader = request.getHeader(TENANT_ID_HEADER);
            String userIdHeader = request.getHeader(USER_ID_HEADER);

            if (tenantIdHeader != null && !tenantIdHeader.isEmpty()) {
                Long tenantId = Long.valueOf(tenantIdHeader);
                UserContext.setTenantId(tenantId);
                MDC.put("tenantId", String.valueOf(tenantId));
            } else {
                log.warn("Missing X-Tenant-Id header for request: {}", request.getRequestURI());
                // Depending on business rules, you might want to throw an exception here
                // or handle requests without tenantId differently (e.g., for public endpoints).
                // For now, we'll allow it but log a warning.
            }

            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                Long userId = Long.valueOf(userIdHeader);
                UserContext.setUserId(userId);
                MDC.put("userId", String.valueOf(userId));
            } else {
                log.debug("Missing X-User-Id header for request: {}", request.getRequestURI());
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear the UserContext after the request is processed to prevent data leakage
            UserContext.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
            MDC.remove("orderId");
            MDC.remove("productId");
        }
    }
}
