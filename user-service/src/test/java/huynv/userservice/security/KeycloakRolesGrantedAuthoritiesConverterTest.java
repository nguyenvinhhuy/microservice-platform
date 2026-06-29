package huynv.userservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mapping of supported Keycloak realm roles into Spring Security authorities.
 */
class KeycloakRolesGrantedAuthoritiesConverterTest {

    /**
     * Ensures only supported realm roles are converted into Spring Security authorities.
     *
     * @return Verifies the converter output for supported and unsupported role values.
     */
    @Test
    void convertMapsSupportedRealmRolesOnly() {
        KeycloakRolesGrantedAuthoritiesConverter converter = new KeycloakRolesGrantedAuthoritiesConverter();
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("user", "ADMIN", "ignored"))));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    /**
     * Creates a Jwt instance with deterministic test metadata and caller-provided claims.
     *
     * @param claims Claim map to embed in the JWT.
     * @return Returns a Jwt instance suitable for unit testing role conversion.
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

