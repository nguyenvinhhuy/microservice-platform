package huynv.fileservice.config;

import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.security.JwtUserContextExtractor;
import huynv.fileservice.service.DistributedRateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies request-path-aware distributed rate limiting for upload, download, and presign endpoints.
 */
@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimitFilter.class);

    private final DistributedRateLimitService distributedRateLimitService;
    private final JwtUserContextExtractor jwtUserContextExtractor;

    /**
     * Creates a request filter that enforces upload, download, and pre-signed URL rate limits.
     *
     * @param distributedRateLimitService Distributed rate-limit service used to enforce bucket quotas.
     * @param jwtUserContextExtractor JWT context extractor used to resolve tenant-scoped authenticated users.
     */
    public RequestRateLimitFilter(DistributedRateLimitService distributedRateLimitService, JwtUserContextExtractor jwtUserContextExtractor) {
        this.distributedRateLimitService = Objects.requireNonNull(distributedRateLimitService, "distributedRateLimitService");
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
    }

    /**
     * Applies distributed rate limiting rules before the request reaches the controller layer.
     *
     * @param request Current HTTP request.
     * @param response Current HTTP response.
     * @param filterChain Remaining filter chain.
     * @return Performs side effects by rejecting abusive traffic before business logic executes.
     * @throws ServletException Throws when the servlet container reports a filter error.
     * @throws IOException Throws when I/O fails during filter processing.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI();
        String method = request.getMethod();
        try {
            if ("POST".equalsIgnoreCase(method) && ("/files/upload".equals(path) || "/files/multipart/initiate".equals(path))) {
                AuthenticatedUser user = extractUser(authentication);
                if (user != null) {
                    distributedRateLimitService.checkUpload(user, clientIp);
                }
            } else if ("POST".equalsIgnoreCase(method) && ("/files/presigned-upload".equals(path) || path.contains("/multipart/parts/"))) {
                AuthenticatedUser user = extractUser(authentication);
                if (user != null) {
                    distributedRateLimitService.checkPresign(user, clientIp);
                }
            } else if ("GET".equalsIgnoreCase(method) && (path.endsWith("/download") || path.endsWith("/presigned-download"))) {
                String subject = authenticatedDownloadSubject(authentication, clientIp);
                distributedRateLimitService.checkDownload(subject, clientIp);
            }
        } catch (RuntimeException ex) {
            log.warn("file-service rate limit rejected path={} clientIp={} message={}", path, clientIp, ex.getMessage());
            throw ex;
        }
        filterChain.doFilter(request, response);
    }

    private AuthenticatedUser extractUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return jwtUserContextExtractor.extract(authentication);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String authenticatedDownloadSubject(Authentication authentication, String clientIp) {
        AuthenticatedUser user = extractUser(authentication);
        if (user == null) {
            return "anonymous:" + clientIp;
        }
        return distributedRateLimitService.subject(user, clientIp);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

