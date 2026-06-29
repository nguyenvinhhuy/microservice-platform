package huynv.userservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Converts supported Keycloak realm roles into Spring Security authorities.
 */
public class KeycloakRolesGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Maps supported realm_access roles to Spring Security authorities.
     *
     * @param jwt Validated Keycloak JWT token.
     * @return Returns supported authorities derived from Keycloak role claims.
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = claimAsMap(jwt, "realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        if (roles instanceof Iterable<?> iterable) {
            for (Object rawRole : iterable) {
                if (rawRole == null) {
                    continue;
                }
                String normalized = normalize(String.valueOf(rawRole));
                if (normalized != null) {
                    authorities.add(new SimpleGrantedAuthority(normalized));
                }
            }
        }
        return authorities;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> claimAsMap(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String normalize(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        String normalized = rawRole.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return switch (normalized) {
            case "ROLE_USER", "ROLE_ADMIN", "ROLE_SUPPORT" -> normalized;
            default -> null;
        };
    }
}

