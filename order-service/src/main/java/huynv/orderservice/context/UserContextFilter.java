package huynv.orderservice.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(10)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Skips identity enforcement for public actuator endpoints exposed without authentication.
     *
     * @param request Incoming servlet request used to evaluate endpoint path.
     * @return Returns true when filter must be bypassed for health and prometheus paths.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus");
    }

    /**
     * Extracts trusted identity headers and builds per-request security and MDC context.
     *
     * @param request Incoming servlet request carrying trusted identity headers.
     * @param response Servlet response passed through to downstream filters.
     * @param filterChain Chain used to continue request processing.
     * @return Populates thread-local and MDC context for the request and clears it after completion.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            validateIdentityHeaders(request);
            Long tenantId = parseLong(request.getHeader(TENANT_ID_HEADER));
            Long userId = parseLong(request.getHeader(USER_ID_HEADER));
            Set<String> roles = parseRoles(request.getHeader(ROLES_HEADER));

            if (tenantId != null) {
                UserContext.setTenantId(tenantId);
                MDC.put("tenantId", tenantId.toString());
            }
            if (userId != null) {
                UserContext.setUserId(userId);
                MDC.put("userId", userId.toString());
            }
            UserContext.setRoles(roles);

            if (roles != null && !roles.isEmpty()) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId != null ? userId.toString() : "anonymous",
                        "N/A",
                        roles.stream().map(SimpleGrantedAuthority::new).toList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (StringUtils.hasText(requestId)) {
                MDC.put("requestId", requestId);
            }

            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (!StringUtils.hasText(correlationId)) {
                correlationId = requestId;
            }
            if (StringUtils.hasText(correlationId)) {
                MDC.put("correlationId", correlationId);
            }

            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + ex.getMessage() + "\"}");
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
            MDC.remove("tenantId");
            MDC.remove("userId");
            MDC.remove("orderId");
            MDC.remove("productId");
            MDC.remove("requestId");
            MDC.remove("correlationId");
        }
    }

    /**
     * Enforces strict trust boundary contract requiring gateway identity headers on every request.
     *
     * @param request Incoming servlet request carrying trusted identity context from gateway.
     * @return Throws IllegalArgumentException when required identity headers are missing.
     */
    private void validateIdentityHeaders(HttpServletRequest request) {
        if (!StringUtils.hasText(request.getHeader(TENANT_ID_HEADER))) {
            throw new IllegalArgumentException("Missing required header X-Tenant-Id");
        }
        if (!StringUtils.hasText(request.getHeader(USER_ID_HEADER))) {
            throw new IllegalArgumentException("Missing required header X-User-Id");
        }
        if (!StringUtils.hasText(request.getHeader(ROLES_HEADER))) {
            throw new IllegalArgumentException("Missing required header X-Roles");
        }
    }

    /**
     * Parses a numeric header value into a Long when present.
     *
     * @param value Header value to parse.
     * @return Returns the parsed long value or null when the header is missing or blank.
     */
    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric identity header value");
        }
    }

    /**
     * Parses a comma-separated roles header into a normalized role set.
     *
     * @param value Raw roles header value.
     * @return Returns a normalized set of roles with ROLE_ prefix applied when needed.
     */
    private Set<String> parseRoles(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .collect(Collectors.toSet());
    }
}
