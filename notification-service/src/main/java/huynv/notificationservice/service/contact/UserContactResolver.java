package huynv.notificationservice.service.contact;

import java.util.Optional;

/**
 * Resolves contact information for a user identifier using trusted internal sources.
 */
public interface UserContactResolver {

    /**
     * Resolves contact information for a tenant-scoped user identifier.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to resolve.
     * @return Returns resolved contact information when available.
     */
    Optional<UserContact> resolve(Long tenantId, Long userId);
}

