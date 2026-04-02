package huynv.notificationservice.dto;

import huynv.notificationservice.domain.NotificationChannelType;
import huynv.notificationservice.domain.NotificationPreference;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Represents a user-visible view of a notification preference record.
 *
 * @param channel Channel being configured.
 * @param enabled Whether delivery through the channel is enabled.
 * @param updatedAt Timestamp when the preference was last updated.
 */
public record NotificationPreferenceResponse(
        NotificationChannelType channel,
        boolean enabled,
        OffsetDateTime updatedAt
) {

    /**
     * Maps a NotificationPreference entity into an API response DTO.
     *
     * @param preference Preference entity to map.
     * @return Returns a response DTO representing the preference.
     */
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        Objects.requireNonNull(preference, "preference");
        return new NotificationPreferenceResponse(preference.getChannel(), preference.isEnabled(), preference.getUpdatedAt());
    }
}

