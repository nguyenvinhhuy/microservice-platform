package huynv.fileservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Objects;

/**
 * Configures JWT-based authentication and authorization for file-service.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final InternalAccessEvaluator internalAccessEvaluator;

    /**
     * Creates the security configuration with the internal access evaluator required for machine-to-machine authorization.
     *
     * @param internalAccessEvaluator Evaluator used to authorize trusted internal callers.
     * @return Initializes the security configuration instance.
     */
    public SecurityConfig(InternalAccessEvaluator internalAccessEvaluator) {
        this.internalAccessEvaluator = Objects.requireNonNull(internalAccessEvaluator, "internalAccessEvaluator");
    }

    /**
     * Configures the actuator security chain with minimal public health exposure and protected internal metrics access.
     *
     * @param http Spring Security HTTP configuration builder.
     * @return Returns the configured security filter chain.
     * @throws Exception Throws when Spring Security fails to build the filter chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/liveness").permitAll()
                        .requestMatchers("/actuator/health/readiness", "/actuator/prometheus", "/actuator/info")
                        .access((authentication, context) -> internalEndpointAuthorizationDecision(authentication.get(), context.getRequest()))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Configures the main application security chain for public, authenticated, and internal file-service APIs.
     *
     * @param http Spring Security HTTP configuration builder.
     * @return Returns the configured application security filter chain.
     * @throws Exception Throws when Spring Security fails to build the filter chain.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/*/download", "/files/*/presigned-download", "/files/download-tickets/*/download").permitAll()
                        .requestMatchers("/internal/**").access((authentication, context) -> internalAuthorizationDecision(authentication.get()))
                        .requestMatchers(HttpMethod.GET, "/files/**").authenticated()
                        .requestMatchers("/files/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Configures JWT role extraction from Keycloak realm roles.
     *
     * @return Returns a JWT authentication converter that maps supported Keycloak roles.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesGrantedAuthoritiesConverter());
        return converter;
    }

    /**
     * Creates an authorization decision for trusted internal traffic.
     *
     * @param authentication Current authentication object.
     * @return Returns an authorization decision based on machine-to-machine JWT claims.
     */
    private AuthorizationDecision internalAuthorizationDecision(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(internalAccessEvaluator.hasInternalAccess(jwtAuthenticationToken.getToken()));
    }

    /**
     * Creates an authorization decision for internal actuator endpoints using either trusted service credentials or local container probes.
     *
     * @param authentication Current authentication object.
     * @param request Current HTTP servlet request.
     * @return Returns an authorization decision for readiness, metrics, and info endpoints.
     */
    private AuthorizationDecision internalEndpointAuthorizationDecision(Authentication authentication, HttpServletRequest request) {
        if (isLocalProbe(request)) {
            return new AuthorizationDecision(true);
        }
        return internalAuthorizationDecision(authentication);
    }

    /**
     * Determines whether the request originated from the local container network namespace for kube exec probes.
     *
     * @param request Current HTTP servlet request.
     * @return Returns true when the request originates from a loopback address.
     */
    private boolean isLocalProbe(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String remoteAddress = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress);
    }
}

