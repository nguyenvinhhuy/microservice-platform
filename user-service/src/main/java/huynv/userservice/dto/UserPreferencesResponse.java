package huynv.userservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents tenant-scoped user preferences exposed through the REST API.
 *
 * @param id Preference row identifier.
 * @param userId Domain user identifier owning the preferences.
 * @param emailEnabled Flag indicating whether email notifications are enabled.
 * @param smsEnabled Flag indicating whether SMS notifications are enabled.
 * @param pushEnabled Flag indicating whether push notifications are enabled.
 * @param marketingEnabled Flag indicating whether marketing notifications are enabled.
 * @param language Preferred language.
 * @param createdAt Creation timestamp.
 * @param updatedAt Last update timestamp.
 * @return Returns an immutable user-preferences response.
 */
public record UserPreferencesResponse(
        UUID id,
        UUID userId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        boolean marketingEnabled,
        String language,
        Instant createdAt,
        Instant updatedAt
) {
}

