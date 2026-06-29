package huynv.fileservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extracts Keycloak realm roles from a validated JWT and exposes them as Spring Security authorities.
 */
public class KeycloakRolesGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Converts supported Keycloak realm roles into Spring Security granted authorities.
     *
     * @param jwt Validated JWT token.
     * @return Returns a collection of granted authorities extracted from the token.
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Map<String, Object> realmAccess = claimAsMap(jwt, "realm_access");
        Object rawRoles = realmAccess == null ? null : realmAccess.get("roles");
        if (rawRoles instanceof Iterable<?> iterable) {
            for (Object rawRole : iterable) {
                if (rawRole == null) {
                    continue;
                }
                String role = String.valueOf(rawRole).trim().toUpperCase();
                if (role.isBlank()) {
                    continue;
                }
                if (!role.startsWith("ROLE_")) {
                    role = "ROLE_" + role;
                }
                authorities.add(new SimpleGrantedAuthority(role));
            }
        }
        return authorities;
    }

    /**
     * Reads a JWT claim as a typed map when the claim is present and structured as a map.
     *
     * @param jwt Validated JWT token.
     * @param claimName Claim name to inspect.
     * @return Returns the claim value as a map, or null when unavailable.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> claimAsMap(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

