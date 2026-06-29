package huynv.fileservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configures Redis-backed cache regions with tenant-safe TTLs for metadata, quotas, and pre-signed upload state.
 */
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableCaching
public class CacheConfig {

    /**
     * Creates a cache manager with explicit cache TTLs for file-service use cases.
     *
     * @param connectionFactory Redis connection factory used by the cache manager.
     * @param properties File-service properties containing cache TTL configuration.
     * @return Returns a RedisCacheManager with named cache configurations.
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            FileServiceProperties properties
    ) {
        Objects.requireNonNull(connectionFactory, "connectionFactory");
        Objects.requireNonNull(properties, "properties");
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder().build();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();
        configurations.put("fileMetadata", defaults.entryTtl(properties.getCache().getMetadataTtl()));
        configurations.put("fileQuota", defaults.entryTtl(properties.getCache().getQuotaTtl()));
        configurations.put("presignState", defaults.entryTtl(properties.getCache().getPresignStateTtl()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configurations)
                .build();
    }
}

