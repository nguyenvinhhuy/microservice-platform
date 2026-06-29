package huynv.userservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configures tenant-aware Redis cache regions used by user-service read models.
 */
@Configuration
public class CacheConfig {

    /**
     * Creates a Redis-backed cache manager with per-cache TTL settings for user lookups and preferences.
     *
     * @param connectionFactory Redis connection factory provided by Spring Boot.
     * @param properties User-service cache properties.
     * @return Returns a CacheManager backed by Redis with cache-specific TTL configuration.
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            UserServiceProperties properties
    ) {
        Objects.requireNonNull(connectionFactory, "connectionFactory");
        Objects.requireNonNull(properties, "properties");

        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder().build();
        RedisSerializationContext.SerializationPair<Object> valuePair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(valuePair)
                .disableCachingNullValues()
                .entryTtl(properties.getCache().getUserLookupTtl());

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                "user-profile-by-id", ttl(defaults, properties.getCache().getUserLookupTtl()),
                "user-profile-by-keycloak", ttl(defaults, properties.getCache().getUserLookupTtl()),
                "user-preferences", ttl(defaults, properties.getCache().getPreferencesTtl())
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Applies a cache-specific TTL to the provided Redis cache configuration.
     *
     * @param configuration Base Redis cache configuration.
     * @param ttl Time-to-live to apply to the cache entries.
     * @return Returns the Redis cache configuration with the requested TTL.
     */
    private RedisCacheConfiguration ttl(RedisCacheConfiguration configuration, Duration ttl) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(ttl, "ttl");
        return configuration.entryTtl(ttl);
    }
}

