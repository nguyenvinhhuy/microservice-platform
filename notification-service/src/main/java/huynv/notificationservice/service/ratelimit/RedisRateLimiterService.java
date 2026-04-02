package huynv.notificationservice.service.ratelimit;

import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.config.NotificationRateLimitOverridesProperties;
import huynv.notificationservice.domain.NotificationChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Implements a Redis-backed token bucket rate limiter using a Lua script.
 */
@Service
@Profile("!test")
public class RedisRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>(
            """
            local tokensKey = KEYS[1]
            local tsKey = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refillPerSec = tonumber(ARGV[2])
            local nowMillis = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttlMillis = tonumber(ARGV[5])

            local lastTokens = tonumber(redis.call('GET', tokensKey))
            if lastTokens == nil then
              lastTokens = capacity
            end
            local lastTs = tonumber(redis.call('GET', tsKey))
            if lastTs == nil then
              lastTs = nowMillis
            end

            local deltaMillis = math.max(0, nowMillis - lastTs)
            local refill = (deltaMillis / 1000.0) * refillPerSec
            local tokens = math.min(capacity, lastTokens + refill)

            local allowed = 0
            if tokens >= requested then
              tokens = tokens - requested
              allowed = 1
            end

            redis.call('SET', tokensKey, tokens, 'PX', ttlMillis)
            redis.call('SET', tsKey, nowMillis, 'PX', ttlMillis)
            return allowed
            """,
            Long.class
    );

    private final NotificationProperties properties;
    private final NotificationRateLimitOverridesProperties overrides;
    private final StringRedisTemplate redisTemplate;

    /**
     * Creates a Redis-backed rate limiter service.
     *
     * @param properties Notification properties containing rate limit settings.
     * @param overrides Optional override properties supporting notification.rateLimit.* keys.
     * @param redisTemplate Redis template used to execute token bucket scripts.
     * @return Initializes a Redis-backed rate limiter service.
     */
    public RedisRateLimiterService(NotificationProperties properties,
                                   NotificationRateLimitOverridesProperties overrides,
                                   StringRedisTemplate redisTemplate) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    /**
     * Attempts to acquire a single permit from a per-channel token bucket in Redis.
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
        int burst = Math.max(1, limitBurst(channel, perSecond));

        String prefix = "notification:rate:" + channel.name() + ":" + tenantId;
        String tokensKey = prefix + ":tokens";
        String tsKey = prefix + ":ts";
        long nowMillis = System.currentTimeMillis();
        long ttlMillis = Duration.ofSeconds(10).toMillis();

        try {
            Long allowed = redisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(tokensKey, tsKey),
                    String.valueOf(burst),
                    String.valueOf(perSecond),
                    String.valueOf(nowMillis),
                    "1",
                    String.valueOf(ttlMillis)
            );
            return allowed != null && allowed == 1L;
        } catch (Exception ex) {
            log.warn("Redis rate limiter backend failed channel={} tenantId={} error={}", channel, tenantId, ex.getMessage());
            throw new RateLimiterBackendException("Redis rate limiter backend failed.", ex);
        }
    }

    private int limitPerSecond(NotificationChannelType channel) {
        Integer override = overridePerSecond(channel);
        if (override != null) {
            return override;
        }
        return switch (channel) {
            case EMAIL -> properties.getRateLimits().getEmailPerSecond();
            case SMS -> properties.getRateLimits().getSmsPerSecond();
            case PUSH -> properties.getRateLimits().getPushPerSecond();
        };
    }

    /**
     * Resolves the token bucket burst capacity for a channel using override configuration when present.
     *
     * @param channel Channel being rate limited.
     * @param perSecond Per-second rate used as a fallback capacity when not configured.
     * @return Returns the resolved burst capacity for the channel.
     */
    private int limitBurst(NotificationChannelType channel, int perSecond) {
        Integer override = overrideBurst(channel);
        if (override != null) {
            return override;
        }
        return switch (channel) {
            case EMAIL -> properties.getRateLimits().getEmailBurst();
            case SMS -> properties.getRateLimits().getSmsBurst();
            case PUSH -> properties.getRateLimits().getPushBurst();
        };
    }

    /**
     * Resolves an optional per-second rate limit override for a channel.
     *
     * @param channel Channel being rate limited.
     * @return Returns the per-second override when configured, otherwise null.
     */
    private Integer overridePerSecond(NotificationChannelType channel) {
        NotificationRateLimitOverridesProperties.ChannelRateLimit config = overrideConfig(channel);
        if (config == null) {
            return null;
        }
        return config.getPerSecond();
    }

    /**
     * Resolves an optional burst capacity override for a channel.
     *
     * @param channel Channel being rate limited.
     * @return Returns the burst capacity override when configured, otherwise null.
     */
    private Integer overrideBurst(NotificationChannelType channel) {
        NotificationRateLimitOverridesProperties.ChannelRateLimit config = overrideConfig(channel);
        if (config == null) {
            return null;
        }
        return config.getBurst();
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
}

