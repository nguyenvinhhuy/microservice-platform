package huynv.userservice.repository;

import huynv.userservice.domain.UserMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for user memberships.
 */
public interface UserMembershipRepository extends JpaRepository<UserMembershipEntity, UUID> {

    /**
     * Lists memberships for a tenant-scoped user.
     *
     * @param tenantId Tenant identifier owning the memberships.
     * @param userId Domain user identifier owning the memberships.
     * @return Returns all memberships belonging to the tenant-scoped user.
     */
    List<UserMembershipEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    /**
     * Lists memberships for multiple tenant-scoped users in one query.
     *
     * @param tenantId Tenant identifier owning the memberships.
     * @param userIds Domain user identifiers owning the memberships.
     * @return Returns all memberships belonging to the provided tenant-scoped users.
     */
    List<UserMembershipEntity> findByTenantIdAndUserIdIn(UUID tenantId, Set<UUID> userIds);
}

