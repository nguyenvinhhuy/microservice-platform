package huynv.userservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user address exposed through the REST API.
 *
 * @param id Address identifier.
 * @param label Human-readable address label.
 * @param country Country value.
 * @param city City value.
 * @param district District value.
 * @param addressLine Address line value.
 * @param postalCode Postal code value.
 * @param isDefault Flag indicating whether the address is the default address.
 * @param createdAt Creation timestamp.
 * @param updatedAt Last update timestamp.
 * @return Returns an immutable user-address response.
 */
public record UserAddressResponse(
        UUID id,
        String label,
        String country,
        String city,
        String district,
        String addressLine,
        String postalCode,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}

