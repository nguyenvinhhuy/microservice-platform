package huynv.fileservice.config;

import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.security.JwtUserContextExtractor;
import huynv.fileservice.service.DistributedRateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies request-path-aware distributed rate limiting for upload, presign, and download flows.
 */
@ExtendWith(MockitoExtension.class)
class RequestRateLimitFilterTest {

    @Mock
    private DistributedRateLimitService distributedRateLimitService;

    @Mock
    private JwtUserContextExtractor jwtUserContextExtractor;

    @InjectMocks
    private RequestRateLimitFilter requestRateLimitFilter;

    /**
     * Verifies upload requests use the tenant-aware upload rate-limit policy.
     *
     * @return Performs assertions against the upload rate-limit interaction.
     */
    @Test
    void uploadPathUsesUploadRateLimit() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Set.of("ROLE_USER"));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("principal");
        mockSecurityContext(authentication);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/files/upload");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(jwtUserContextExtractor.extract(authentication)).thenReturn(user);

        requestRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(distributedRateLimitService).checkUpload(user, "10.0.0.1");
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Verifies multipart part pre-signing requests use the presign rate-limit policy.
     *
     * @return Performs assertions against the presign rate-limit interaction.
     */
    @Test
    void multipartPresignPathUsesPresignRateLimit() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Set.of("ROLE_USER"));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("principal");
        mockSecurityContext(authentication);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/files/123e4567-e89b-12d3-a456-426614174000/multipart/parts/1/presign");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(jwtUserContextExtractor.extract(authentication)).thenReturn(user);

        requestRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(distributedRateLimitService).checkPresign(user, "203.0.113.10");
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Verifies download requests use the download rate-limit policy for anonymous subjects.
     *
     * @return Performs assertions against the download rate-limit interaction.
     */
    @Test
    void downloadPathUsesAnonymousDownloadRateLimit() throws Exception {
        mockSecurityContext(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/files/download-tickets/token-123/download");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.4");

        requestRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(distributedRateLimitService).checkDownload(eq("anonymous:198.51.100.4"), eq("198.51.100.4"));
        verify(filterChain).doFilter(request, response);
    }

    private void mockSecurityContext(Authentication authentication) {
        SecurityContext securityContext = new SecurityContextImpl();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}

