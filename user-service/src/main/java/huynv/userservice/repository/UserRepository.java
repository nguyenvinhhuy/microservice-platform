package huynv.userservice.repository;

import huynv.userservice.domain.MembershipRole;
import huynv.userservice.domain.MembershipStatus;
import huynv.userservice.domain.UserEntity;
import huynv.userservice.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides tenant-aware persistence access for user profiles.
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Finds a non-deleted user profile by tenant and domain identifier.
     *
     * @param tenantId Tenant identifier owning the profile.
     * @param id Domain user identifier.
     * @return Returns the matching user profile when present.
     */
    Optional<UserEntity> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

    /**
     * Finds a non-deleted user profile by tenant and Keycloak subject identifier.
     *
     * @param tenantId Tenant identifier owning the profile.
     * @param keycloakUserId Keycloak subject identifier.
     * @return Returns the matching user profile when present.
     */
    Optional<UserEntity> findByTenantIdAndKeycloakUserIdAndDeletedAtIsNull(UUID tenantId, UUID keycloakUserId);

    /**
     * Searches non-deleted tenant users using optional email, status, and role filters.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param email Email substring filter.
     * @param status User status filter.
     * @param role Membership role filter.
     * @param activeStatus Membership status used to restrict role matches.
     * @param pageable Paging configuration.
     * @return Returns a page of tenant-scoped user profiles.
     */
    @Query("""
            select u
            from UserEntity u
            where u.tenantId = :tenantId
              and u.deletedAt is null
              and (:email is null or lower(u.email) like lower(concat('%', :email, '%')) escape '!')
              and (:status is null or u.status = :status)
              and (
                    :role is null or exists (
                        select 1
                        from UserMembershipEntity membership
                        where membership.tenantId = :tenantId
                          and membership.userId = u.id
                          and membership.role = :role
                          and membership.status = :activeStatus
                    )
                  )
            order by u.updatedAt desc
            """)
    Page<UserEntity> searchTenantUsers(
            @Param("tenantId") UUID tenantId,
            @Param("email") String email,
            @Param("status") UserStatus status,
            @Param("role") MembershipRole role,
            @Param("activeStatus") MembershipStatus activeStatus,
            Pageable pageable
    );
}

