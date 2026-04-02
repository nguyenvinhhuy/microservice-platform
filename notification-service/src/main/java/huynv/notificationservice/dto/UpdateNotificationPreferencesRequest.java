package huynv.notificationservice.dto;

import huynv.notificationservice.domain.NotificationChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Defines a request payload for updating notification channel preferences for the current user.
 *
 * @param preferences Channel preference updates to apply.
 */
public record UpdateNotificationPreferencesRequest(
        @NotEmpty @Valid List<PreferenceUpdate> preferences
) {

    /**
     * Defines a single channel preference update.
     *
     * @param channel Channel being configured.
     * @param enabled Whether delivery through the channel is enabled.
     */
    public record PreferenceUpdate(
            @NotNull NotificationChannelType channel,
            boolean enabled
    ) {
    }
}

