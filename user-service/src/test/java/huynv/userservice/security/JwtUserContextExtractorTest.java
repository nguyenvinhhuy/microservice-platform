package huynv.userservice.security;

import huynv.userservice.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies tenant and user identity extraction from validated JWT claims.
 */
class JwtUserContextExtractorTest {

    private final JwtUserContextExtractor extractor = new JwtUserContextExtractor();

    /**
     * Ensures the extractor returns the expected tenant-scoped authenticated user context.
     *
     * @return Verifies the extracted user identifier, tenant identifier, and roles.
     */
    @Test
    void extractReturnsTenantScopedAuthenticatedUser() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt(Map.of(
                "sub", userId.toString(),
                "tenantId", tenantId.toString(),
                "realm_access", Map.of("roles", List.of("user", "support"))
        )));

        AuthenticatedUser authenticatedUser = extractor.extract(authentication);

        assertThat(authenticatedUser.userId()).isEqualTo(userId);
        assertThat(authenticatedUser.tenantId()).isEqualTo(tenantId);
        assertThat(authenticatedUser.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_SUPPORT");
    }

    /**
     * Ensures the extractor rejects JWTs that do not carry a valid tenant identifier claim.
     *
     * @return Verifies that a BadRequestException is raised for invalid tenant claims.
     */
    @Test
    void extractRejectsJwtWithoutTenantClaim() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt(Map.of(
                "sub", UUID.randomUUID().toString(),
                "realm_access", Map.of("roles", List.of("user"))
        )));

        assertThatThrownBy(() -> extractor.extract(authentication))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenantId claim is required");
    }

    /**
     * Creates a Jwt instance with deterministic test metadata and caller-provided claims.
     *
     * @param claims Claim map to embed in the JWT.
     * @return Returns a Jwt instance suitable for unit testing JWT extraction.
     */
    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.parse("2026-04-29T00:00:00Z"),
                Instant.parse("2026-04-29T01:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
    }
}

