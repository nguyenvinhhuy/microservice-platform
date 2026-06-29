package huynv.fileservice.security;

import java.util.Set;
import java.util.UUID;

/**
 * Describes the tenant-scoped authenticated user derived from a validated JWT.
 *
 * @param userId User identifier from the JWT subject.
 * @param tenantId Tenant identifier derived from trusted JWT claims.
 * @param roles Normalized Spring Security-style role names.
 * @return Returns an immutable authenticated user context.
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, Set<String> roles) {

    /**
     * Determines whether the authenticated user has administrative or support privileges.
     *
     * @return Returns true when the user has an elevated role.
     */
    public boolean isPrivileged() {
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPPORT");
    }
}

