package huynv.event.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the payload emitted when a tenant-scoped user preference set is updated.
 *
 * @param userId Domain user identifier.
 * @param tenantId Tenant identifier owning the preference set.
 * @param emailEnabled Flag indicating whether email notifications are enabled.
 * @param smsEnabled Flag indicating whether SMS notifications are enabled.
 * @param pushEnabled Flag indicating whether push notifications are enabled.
 * @param marketingEnabled Flag indicating whether marketing notifications are enabled.
 * @param language Preferred language configured by the user.
 * @param updatedAt Update timestamp for the preference set.
 * @return Returns an immutable payload describing updated user preferences.
 */
public record UserPreferencesUpdatedEvent(
        UUID userId,
        UUID tenantId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        boolean marketingEnabled,
        String language,
        Instant updatedAt
) {
}

