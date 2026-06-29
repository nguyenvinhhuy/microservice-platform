package huynv.userservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a tenant-scoped user profile exposed through the REST API.
 *
 * @param id Domain user identifier.
 * @param keycloakUserId Keycloak subject identifier linked to the profile.
 * @param tenantId Tenant identifier owning the profile.
 * @param email Email address.
 * @param fullName Full display name.
 * @param phoneNumber Phone number.
 * @param avatarUrl Avatar URL.
 * @param status Lifecycle status.
 * @param locale Preferred locale.
 * @param timezone Preferred timezone.
 * @param memberships Persisted tenant memberships.
 * @param createdAt Creation timestamp.
 * @param updatedAt Last update timestamp.
 * @return Returns an immutable user-profile response.
 */
public record UserProfileResponse(
        UUID id,
        UUID keycloakUserId,
        UUID tenantId,
        String email,
        String fullName,
        String phoneNumber,
        String avatarUrl,
        String status,
        String locale,
        String timezone,
        List<UserMembershipResponse> memberships,
        Instant createdAt,
        Instant updatedAt
) {
}

