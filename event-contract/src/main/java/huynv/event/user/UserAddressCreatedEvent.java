package huynv.event.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the payload emitted when a tenant-scoped user address is created.
 *
 * @param userId Domain user identifier.
 * @param tenantId Tenant identifier owning the address.
 * @param addressId Address identifier.
 * @param label Human-readable address label.
 * @param country Country value stored for the address.
 * @param city City value stored for the address.
 * @param district District value stored for the address.
 * @param addressLine Address line stored for the address.
 * @param postalCode Postal code stored for the address.
 * @param isDefault Flag indicating whether the address is the default address.
 * @param createdAt Creation timestamp for the address.
 * @return Returns an immutable payload describing a newly created user address.
 */
public record UserAddressCreatedEvent(
        UUID userId,
        UUID tenantId,
        UUID addressId,
        String label,
        String country,
        String city,
        String district,
        String addressLine,
        String postalCode,
        boolean isDefault,
        Instant createdAt
) {
}

