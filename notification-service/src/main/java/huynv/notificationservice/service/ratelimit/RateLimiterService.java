package huynv.notificationservice.service.ratelimit;

import huynv.notificationservice.domain.NotificationChannelType;

/**
 * Applies per-channel rate limiting for external provider protection.
 */
public interface RateLimiterService {

    /**
     * Attempts to acquire a single permit for a channel rate limit key.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param channel Channel being rate limited.
     * @return Returns true when a permit was acquired.
     */
    boolean tryAcquire(Long tenantId, NotificationChannelType channel);
}

