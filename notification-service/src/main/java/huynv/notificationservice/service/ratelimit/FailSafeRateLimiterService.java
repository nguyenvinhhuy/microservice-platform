package huynv.notificationservice.service.ratelimit;

import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.config.NotificationRateLimitOverridesProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides a Redis-backed rate limiter with a bounded in-memory fallback when Redis is unavailable.
 */
@Service
@Profile("!test")
@Primary
public class FailSafeRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(FailSafeRateLimiterService.class);

    private final NotificationProperties properties;
    private final NotificationRateLimitOverridesProperties overrides;
    private final RedisRateLimiterService redisRateLimiterService;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Creates a fail-safe rate limiter that prefers Redis but falls back to in-memory limits on backend failures.
     *
     * @param properties Notification properties containing per-channel rate limit settings.
     * @param overrides Optional override properties supporting notification.rateLimit.* keys.
     * @param redisRateLimiterService Redis-backed limiter used as the primary backend.
     * @return Initializes a fail-safe rate limiter service.
     */
    public FailSafeRateLimiterService(NotificationProperties properties,
                                      NotificationRateLimitOverridesProperties overrides,
                                      RedisRateLimiterService redisRateLimiterService) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.redisRateLimiterService = Objects.requireNonNull(redisRateLimiterService, "redisRateLimiterService");
    }

    /**
     * Attempts to acquire a permit from Redis, falling back to a per-second in-memory window when Redis fails.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param channel Channel being rate limited.
     * @return Returns true when a permit was acquired, otherwise false.
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

        try {
            return redisRateLimiterService.tryAcquire(tenantId, channel);
        } catch (RateLimiterBackendException ex) {
            boolean allowed = tryAcquireInMemory(tenantId, channel, burst, perSecond);
            log.warn("Rate limiter fallback used channel={} tenantId={} allowed={} message={}",
                    channel,
                    tenantId,
                    allowed,
                    ex.getMessage());
            return allowed;
        }
    }

    /**
     * Attempts to acquire a permit from an in-memory token bucket for a given tenant and channel.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param channel Channel being rate limited.
     * @param burst Maximum number of tokens allowed in the bucket.
     * @param perSecond Refill rate per second used for steady-state throughput.
     * @return Returns true when a permit was acquired, otherwise false.
     */
    private boolean tryAcquireInMemory(Long tenantId, NotificationChannelType channel, int burst, int perSecond) {
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
        NotificationRateLimitOverridesProperties.ChannelRateLimit config = overrideConfig(channel);
        if (config != null && config.getPerSecond() != null) {
            return config.getPerSecond();
        }
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
        NotificationRateLimitOverridesProperties.ChannelRateLimit config = overrideConfig(channel);
        if (config != null && config.getBurst() != null) {
            return config.getBurst();
        }
        return switch (channel) {
            case EMAIL -> properties.getRateLimits().getEmailBurst();
            case SMS -> properties.getRateLimits().getSmsBurst();
            case PUSH -> properties.getRateLimits().getPushBurst();
        };
    }

    /**
     * Selects the override configuration bucket for the provided channel.
     *
     * @param channel Channel being rate limited.
     * @return Returns the channel-specific override configuration.
     */
    private NotificationRateLimitOverridesProperties.ChannelRateLimit overrideConfig(NotificationChannelType channel) {
        return switch (channel) {
            case EMAIL -> overrides.getEmail();
            case SMS -> overrides.getSms();
            case PUSH -> overrides.getPush();
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

