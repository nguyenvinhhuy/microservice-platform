package huynv.userservice.dto;

import huynv.userservice.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Captures mutable profile fields for the current authenticated user.
 *
 * @param email Email address to persist.
 * @param fullName Full display name to persist.
 * @param phoneNumber Phone number to persist.
 * @param avatarUrl Avatar URL to persist.
 * @param status Lifecycle status to persist.
 * @param locale Preferred locale to persist.
 * @param timezone Preferred timezone to persist.
 * @return Returns an immutable update-profile request.
 */
public record UpdateUserProfileRequest(
        @Email(message = "Email must be valid.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email,
        @Size(max = 255, message = "Full name must not exceed 255 characters.")
        String fullName,
        @Pattern(regexp = "^$|^[+]?[0-9()\\-\\s]{7,30}$", message = "Phone number must be a valid international or local number.")
        String phoneNumber,
        @Size(max = 512, message = "Avatar URL must not exceed 512 characters.")
        String avatarUrl,
        UserStatus status,
        @Size(max = 32, message = "Locale must not exceed 32 characters.")
        String locale,
        @Size(max = 64, message = "Timezone must not exceed 64 characters.")
        String timezone
) {
}

