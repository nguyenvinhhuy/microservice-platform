package huynv.userservice.security;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the tenant-scoped identity extracted from a validated JWT.
 *
 * @param userId Keycloak subject identifier from the JWT.
 * @param tenantId Tenant identifier from the JWT.
 * @param roles Granted roles mapped from Keycloak realm roles.
 * @return Returns an immutable authenticated-user context.
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, Set<String> roles) {

    /**
     * Determines whether the current user has any privileged support role.
     *
     * @return Returns true when the user has ROLE_ADMIN or ROLE_SUPPORT.
     */
    public boolean isAdminOrSupport() {
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPPORT");
    }
}

