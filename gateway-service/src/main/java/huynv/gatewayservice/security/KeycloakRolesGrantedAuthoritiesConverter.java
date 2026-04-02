package huynv.gatewayservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Converts Keycloak JWT role claims into Spring Security authorities prefixed with `ROLE_`.
 */
public class KeycloakRolesGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Extracts realm roles from the Keycloak JWT and maps them into Spring Security `GrantedAuthority` values.
     *
     * @param jwt JWT token issued by Keycloak.
     * @return Returns granted authorities derived from JWT role claims.
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = claimAsMap(jwt, "realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        if (roles instanceof Iterable<?> iterable) {
            for (Object role : iterable) {
                if (role == null) {
                    continue;
                }
                String roleName = String.valueOf(role).trim();
                if (!roleName.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase()));
                }
            }
        }
        return authorities;
    }

    /**
     * Extracts a claim as a map, returning null when the claim is missing or not a map.
     *
     * @param jwt JWT token containing claims.
     * @param claim Claim name to read.
     * @return Returns the claim value as a map, or null when absent or not a map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> claimAsMap(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

