package huynv.notificationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Extracts tenant and user identifiers from a validated JWT for request authorization decisions.
 */
@Component
public class JwtTenantUserExtractor {

    /**
     * Extracts tenantId and userId claims from the authentication principal when available.
     *
     * @param authentication Authentication holding a validated JWT principal.
     * @return Returns a TenantUserContext containing parsed tenantId and userId values, or nulls when missing.
     */
    public TenantUserContext extract(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return new TenantUserContext(null, null);
        }
        Long tenantId = longClaim(jwt, "tenantId", "tenant_id");
        Long userId = longClaim(jwt, "userId", "user_id");
        return new TenantUserContext(tenantId, userId);
    }

    /**
     * Reads a numeric claim from a JWT using one or more candidate claim keys.
     *
     * @param jwt JWT containing claim values.
     * @param keys Candidate claim keys to search in order.
     * @return Returns a parsed long claim value or null when no supported claim is present.
     */
    private static Long longClaim(Jwt jwt, String... keys) {
        Objects.requireNonNull(jwt, "jwt");
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = jwt.getClaim(key);
            Long parsed = parseLong(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * Parses a claim value into a Long when the value is numeric or numeric text.
     *
     * @param value Claim value extracted from the JWT.
     * @return Returns a parsed long value or null when parsing is not possible.
     */
    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
