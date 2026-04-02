package huynv.notificationservice.service.ratelimit;

import huynv.eventinfra.config.NotificationProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides an in-memory rate limiter for test profiles and environments without Redis.
 */
@Service
@Profile("test")
@Primary
public class InMemoryRateLimiterService implements RateLimiterService {

    private final NotificationProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Creates an in-memory rate limiter using configured per-channel limits.
     *
     * @param properties Notification properties containing rate limit settings.
     * @return Initializes an in-memory rate limiter service.
     */
    public InMemoryRateLimiterService(NotificationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Attempts to acquire a permit within the current second window.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param channel Channel being rate limited.
     * @return Returns true when a permit was acquired.
     */
    @Override
    public boolean tryAcquire(Long tenantId, NotificationChannelType channel) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(channel, "channel");

        int perSecond = limitPerSecond(channel);
        if (perSecond <= 0) {
            return true;
        }
        int burst = Math.max(1, limitBurst(channel));
        long nowMillis = System.currentTimeMillis();
        String key = channel.name() + ":" + tenantId;
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> TokenBucket.create(burst, perSecond, nowMillis));
        return bucket.tryAcquire(burst, perSecond, nowMillis);
    }

    /**
     * Resolves the configured per-second rate limit for a specific channel.
     *
     * @param channel Channel being rate limited.
     * @return Returns the configured per-second limit for the channel.
     */
    private int limitPerSecond(NotificationChannelType channel) {
        return switch (channel) {
            case EMAIL -> properties.getRateLimits().getEmailPerSecond();
            case SMS -> properties.getRateLimits().getSmsPerSecond();
            case PUSH -> properties.getRateLimits().getPushPerSecond();
        };
    }

    /**
     * Resolves the configured burst capacity for a specific channel.
     *
     * @param channel Channel being rate limited.
     * @return Returns the configured burst capacity for the channel.
     */
    private int limitBurst(NotificationChannelType channel) {
        return switch (channel) {
            case EMAIL -> properties.getRateLimits().getEmailBurst();
            case SMS -> properties.getRateLimits().getSmsBurst();
            case PUSH -> properties.getRateLimits().getPushBurst();
        };
    }

    private static final class TokenBucket {
        private final AtomicLong lastMillis;
        private final AtomicLong tokensMicros;

        private TokenBucket(long lastMillis, long tokensMicros) {
            this.lastMillis = new AtomicLong(lastMillis);
            this.tokensMicros = new AtomicLong(tokensMicros);
        }

        /**
         * Creates a token bucket initialized to full capacity.
         *
         * @param capacity Maximum number of tokens allowed in the bucket.
         * @param perSecond Per-second refill rate used for steady-state throughput.
         * @param nowMillis Current timestamp in milliseconds.
         * @return Returns a token bucket initialized for rate limiting decisions.
         */
        private static TokenBucket create(int capacity, int perSecond, long nowMillis) {
            long initialMicros = Math.max(0, capacity) * 1_000_000L;
            return new TokenBucket(nowMillis, initialMicros);
        }

        /**
         * Attempts to acquire one token from the bucket while refilling based on elapsed time.
         *
         * @param capacity Maximum number of tokens allowed in the bucket.
         * @param perSecond Per-second refill rate used for steady-state throughput.
         * @param nowMillis Current timestamp in milliseconds.
         * @return Returns true when a token was acquired, otherwise false.
         */
        private boolean tryAcquire(int capacity, int perSecond, long nowMillis) {
            long capMicros = Math.max(0, capacity) * 1_000_000L;
            long refillPerSecMicros = Math.max(0, perSecond) * 1_000_000L;

            long prevMillis = lastMillis.getAndSet(nowMillis);
            long deltaMillis = Math.max(0, nowMillis - prevMillis);
            long refillMicros = (deltaMillis * refillPerSecMicros) / Duration.ofSeconds(1).toMillis();

            while (true) {
                long current = tokensMicros.get();
                long next = Math.min(capMicros, current + refillMicros);
                if (next < 1_000_000L) {
                    if (tokensMicros.compareAndSet(current, next)) {
                        return false;
                    }
                    continue;
                }
                long after = next - 1_000_000L;
                if (tokensMicros.compareAndSet(current, after)) {
                    return true;
                }
            }
        }
    }
}

