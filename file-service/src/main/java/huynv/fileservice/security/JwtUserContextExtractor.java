package huynv.fileservice.security;

import huynv.fileservice.exception.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Extracts tenant-scoped identity information from a validated JWT.
 */
@Component
public class JwtUserContextExtractor {

    /**
     * Extracts the authenticated user context from the current Spring Security authentication.
     *
     * @param authentication Authentication holding the validated JWT principal.
     * @return Returns the authenticated user context required for authorization and tenancy checks.
     */
    public AuthenticatedUser extract(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadRequestException("AUTHENTICATION_REQUIRED", "A validated JWT is required.");
        }
        UUID userId = uuidClaim(jwt, "sub");
        UUID tenantId = extractTenantId(jwt);
        Set<String> roles = extractRoles(jwt);
        return new AuthenticatedUser(userId, tenantId, roles);
    }

    /**
     * Extracts the tenant identifier from a validated JWT for internal service flows.
     *
     * @param jwt Validated JWT token.
     * @return Returns the tenant identifier claim required for tenant-scoped data access.
     */
    public UUID extractTenantId(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        return firstUuidClaim(jwt, "tenantId", "tenant_id");
    }

    /**
     * Extracts supported role names from the Keycloak realm_access claim.
     *
     * @param jwt Validated JWT token.
     * @return Returns normalized application role names.
     */
    public Set<String> extractRoles(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Set<String> roles = new LinkedHashSet<>();
        Map<String, Object> realmAccess = claimAsMap(jwt, "realm_access");
        Object rawRoles = realmAccess == null ? null : realmAccess.get("roles");
        if (rawRoles instanceof Iterable<?> iterable) {
            for (Object rawRole : iterable) {
                if (rawRole == null) {
                    continue;
                }
                String normalized = normalizeRole(String.valueOf(rawRole));
                if (normalized != null) {
                    roles.add(normalized);
                }
            }
        }
        return Set.copyOf(roles);
    }

    /**
     * Extracts the optional authenticated user identifier from the current authentication when available.
     *
     * @param authentication Current Spring Security authentication.
     * @return Returns the authenticated user identifier, or null when no JWT-backed user is present.
     */
    public UUID tryExtractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return parseUuid(jwt.getClaim("sub"));
    }

    private static UUID uuidClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        UUID parsed = parseUuid(value);
        if (parsed == null) {
            throw new BadRequestException("INVALID_JWT_CLAIMS", "JWT claim '" + claimName + "' is required and must be a UUID.");
        }
        return parsed;
    }

    private static UUID firstUuidClaim(Jwt jwt, String... claimNames) {
        if (claimNames == null) {
            throw new BadRequestException("INVALID_JWT_CLAIMS", "JWT tenant claim names are missing.");
        }
        for (String claimName : claimNames) {
            UUID parsed = parseUuid(jwt.getClaim(claimName));
            if (parsed != null) {
                return parsed;
            }
        }
        throw new BadRequestException("INVALID_JWT_CLAIMS", "JWT tenantId claim is required and must be a UUID.");
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    /**
     * Normalizes a raw Keycloak role name into a Spring Security role string.
     *
     * @param rawRole Raw role value from the token.
     * @return Returns a normalized role string, or null when the role is blank.
     */
    private static String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        String normalized = rawRole.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return normalized;
    }
}

