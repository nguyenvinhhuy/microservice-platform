package huynv.productservice.filter;

import huynv.productservice.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String ROLES_HEADER = "X-Roles";

    private static final String MDC_USER_ID_KEY = "userId";
    private static final String MDC_TENANT_ID_KEY = "tenantId";

    /**
     * Extracts user and tenant headers, sets thread-local user context, and clears it after request.
     *
     * @param request incoming servlet request carrying identity headers
     * @param response servlet response passed through to downstream filters
     * @param filterChain chain used to continue request processing
     * @return performs side effects defined by this operation
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UserContext userContext = new UserContext();

            String userIdStr = request.getHeader(USER_ID_HEADER);
            if (userIdStr != null && !userIdStr.isEmpty()) {
                userContext.setUserId(Long.parseLong(userIdStr));
                MDC.put(MDC_USER_ID_KEY, userIdStr);
            }

            String tenantIdStr = request.getHeader(TENANT_ID_HEADER);
            if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
                userContext.setTenantId(Long.parseLong(tenantIdStr));
                MDC.put(MDC_TENANT_ID_KEY, tenantIdStr);
            }

            String rolesStr = request.getHeader(ROLES_HEADER);
            if (rolesStr != null && !rolesStr.isEmpty()) {
                List<String> roles = Arrays.asList(rolesStr.split(","));
                userContext.setRoles(roles);
            } else {
                userContext.setRoles(Collections.emptyList());
            }

            UserContext.setCurrentUserContext(userContext);

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            MDC.remove(MDC_USER_ID_KEY);
            MDC.remove(MDC_TENANT_ID_KEY);
        }
    }
}
