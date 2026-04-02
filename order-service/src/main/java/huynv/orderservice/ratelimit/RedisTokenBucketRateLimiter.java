package huynv.orderservice.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Implements an atomic Redis-backed token bucket suitable for distributed rate limiting.
 */
@Component
public class RedisTokenBucketRateLimiter {

    private static final String HASH_TOKENS_FIELD = "tokens";
    private static final String HASH_LAST_REFILL_MS_FIELD = "last_refill_ms";

    private static final DefaultRedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local nowMs = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refillTokens = tonumber(ARGV[3])
            local refillPeriodMs = tonumber(ARGV[4])
            local cost = tonumber(ARGV[5])

            local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
            local tokens = tonumber(data[1])
            local lastRefill = tonumber(data[2])

            if tokens == nil then
              tokens = capacity
            end
            if lastRefill == nil then
              lastRefill = nowMs
            end

            if refillPeriodMs > 0 then
              local elapsed = nowMs - lastRefill
              if elapsed > 0 then
                local periods = math.floor(elapsed / refillPeriodMs)
                if periods > 0 then
                  local refill = periods * refillTokens
                  tokens = math.min(capacity, tokens + refill)
                  lastRefill = lastRefill + (periods * refillPeriodMs)
                end
              end
            end

            local allowed = 0
            if tokens >= cost then
              tokens = tokens - cost
              allowed = 1
            end

            redis.call('HSET', key, 'tokens', tokens, 'last_refill_ms', lastRefill)
            local ttlMs = refillPeriodMs * 2
            if ttlMs > 0 then
              redis.call('PEXPIRE', key, ttlMs)
            end
            return { allowed, tokens }
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final OrderRateLimitingProperties properties;

    /**
     * Creates a Redis token bucket rate limiter.
     *
     * @param redisTemplate Redis template used to execute atomic Lua scripts.
     * @param properties Rate limiting configuration properties.
     * @return Initializes a Redis token bucket rate limiter.
     */
    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, OrderRateLimitingProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Attempts to consume a single token from the bucket identified by the given key.
     *
     * @param key Redis key used to scope the token bucket.
     * @return Returns true when a token was consumed and the request is allowed.
     */
    public boolean tryConsumeOne(String key) {
        Objects.requireNonNull(key, "key");
        long nowMs = System.currentTimeMillis();
        long refillPeriodMs = properties.getRefillPeriod().toMillis();
        List<?> result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(properties.getCapacity()),
                String.valueOf(properties.getRefillTokens()),
                String.valueOf(refillPeriodMs),
                "1"
        );
        if (result == null || result.isEmpty()) {
            return true;
        }
        Object allowed = result.getFirst();
        return allowed != null && "1".equals(String.valueOf(allowed));
    }
}

