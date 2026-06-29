package huynv.userservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Captures mutable preference fields for the current authenticated user.
 *
 * @param emailEnabled Flag indicating whether email notifications are enabled.
 * @param smsEnabled Flag indicating whether SMS notifications are enabled.
 * @param pushEnabled Flag indicating whether push notifications are enabled.
 * @param marketingEnabled Flag indicating whether marketing notifications are enabled.
 * @param language Preferred language to persist.
 * @return Returns an immutable update-preferences request.
 */
public record UpdateUserPreferencesRequest(
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        boolean marketingEnabled,
        @Size(max = 32, message = "Language must not exceed 32 characters.")
        String language
) {
}

