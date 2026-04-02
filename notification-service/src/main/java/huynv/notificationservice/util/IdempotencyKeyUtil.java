package huynv.notificationservice.util;

import java.util.Objects;

/**
 * Builds tenant-scoped idempotency keys to prevent cross-tenant deduplication collisions.
 */
public final class IdempotencyKeyUtil {

    private IdempotencyKeyUtil() {
    }

    /**
     * Creates a tenant-scoped key for storing event idempotency markers.
     *
     * @param tenantId Tenant identifier used for isolation.
     * @param eventId Event identifier provided by the upstream system.
     * @return Returns a stable composite key suitable for storing in idempotency tables.
     */
    public static String tenantScopedEventId(Long tenantId, String eventId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        String safeEventId = eventId.trim();
        if (safeEventId.isEmpty()) {
            throw new IllegalArgumentException("eventId must not be blank.");
        }
        return tenantId + ":" + safeEventId;
    }
}

