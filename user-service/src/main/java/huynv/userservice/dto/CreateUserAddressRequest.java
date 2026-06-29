package huynv.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Captures the address fields required to create a tenant-scoped user address.
 *
 * @param label Human-readable address label.
 * @param country Country value to persist.
 * @param city City value to persist.
 * @param district District value to persist.
 * @param addressLine Address line to persist.
 * @param postalCode Postal code to persist.
 * @param isDefault Flag indicating whether the created address becomes the default address.
 * @return Returns an immutable create-address request.
 */
public record CreateUserAddressRequest(
        @Size(max = 80, message = "Label must not exceed 80 characters.")
        String label,
        @NotBlank(message = "Country is required.")
        @Size(max = 100, message = "Country must not exceed 100 characters.")
        String country,
        @NotBlank(message = "City is required.")
        @Size(max = 100, message = "City must not exceed 100 characters.")
        String city,
        @Size(max = 100, message = "District must not exceed 100 characters.")
        String district,
        @NotBlank(message = "Address line is required.")
        @Size(max = 255, message = "Address line must not exceed 255 characters.")
        String addressLine,
        @Size(max = 30, message = "Postal code must not exceed 30 characters.")
        String postalCode,
        boolean isDefault
) {
}

