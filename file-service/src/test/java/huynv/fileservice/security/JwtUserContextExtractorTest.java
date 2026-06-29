package huynv.fileservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies tenant and role extraction from validated JWT claims.
 */
class JwtUserContextExtractorTest {

    private final JwtUserContextExtractor extractor = new JwtUserContextExtractor();

    /**
     * Verifies that the extractor returns the expected authenticated user context from standard JWT claims.
     *
     * @return Performs assertions against the extracted authenticated user context.
     */
    @Test
    void extractReturnsTenantUserAndRoles() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = jwt(Map.of(
                "sub", userId.toString(),
                "tenantId", tenantId.toString(),
                "realm_access", Map.of("roles", List.of("user", "admin"))
        ));

        AuthenticatedUser authenticatedUser = extractor.extract(new TestingAuthenticationToken(jwt, null));

        assertThat(authenticatedUser.userId()).isEqualTo(userId);
        assertThat(authenticatedUser.tenantId()).isEqualTo(tenantId);
        assertThat(authenticatedUser.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    /**
     * Creates a JWT suitable for extractor tests.
     *
     * @param claims JWT claims to embed.
     * @return Returns a test JWT instance.
     */
    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "none"), claims);
    }
}

