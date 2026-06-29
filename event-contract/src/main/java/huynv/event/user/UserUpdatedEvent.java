package huynv.event.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the payload emitted when a tenant-scoped user profile is updated.
 *
 * @param userId Domain user identifier.
 * @param keycloakUserId Keycloak subject identifier linked to the profile.
 * @param tenantId Tenant identifier owning the profile.
 * @param email Email address stored for the profile.
 * @param fullName Full display name stored for the profile.
 * @param phoneNumber Phone number stored for the profile.
 * @param avatarUrl Avatar URL stored for the profile.
 * @param status Current lifecycle status of the profile.
 * @param locale Preferred locale stored for the profile.
 * @param timezone Preferred timezone stored for the profile.
 * @param updatedAt Update timestamp for the profile.
 * @return Returns an immutable payload describing an updated user profile.
 */
public record UserUpdatedEvent(
        UUID userId,
        UUID keycloakUserId,
        UUID tenantId,
        String email,
        String fullName,
        String phoneNumber,
        String avatarUrl,
        String status,
        String locale,
        String timezone,
        Instant updatedAt
) {
}

