package huynv.notificationservice.service.contact;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.eventinfra.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves user contact information with an optional Redis caching layer.
 */
@Service
@Primary
public class CachedUserContactResolver implements UserContactResolver {

    private static final Logger log = LoggerFactory.getLogger(CachedUserContactResolver.class);

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserServiceUserContactResolver userServiceResolver;
    private final ObjectProvider<SyntheticUserContactResolver> fallbackResolver;

    /**
     * Creates a cached resolver that queries user-service and falls back to synthetic contacts when needed.
     *
     * @param properties Notification properties containing Redis caching configuration.
     * @param objectMapper ObjectMapper used to serialize and deserialize cached contact values.
     * @param redisTemplate Redis template used for cache operations.
     * @param userServiceResolver Resolver backed by user-service.
     * @param fallbackResolver Fallback resolver used when user-service is unavailable.
     * @return Initializes a cached user contact resolver.
     */
    public CachedUserContactResolver(NotificationProperties properties,
                                    ObjectMapper objectMapper,
                                    StringRedisTemplate redisTemplate,
                                    UserServiceUserContactResolver userServiceResolver,
                                    ObjectProvider<SyntheticUserContactResolver> fallbackResolver) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.userServiceResolver = Objects.requireNonNull(userServiceResolver, "userServiceResolver");
        this.fallbackResolver = Objects.requireNonNull(fallbackResolver, "fallbackResolver");
    }

    /**
     * Resolves user contact information using cached values when available.
     *
     * @param tenantId Tenant identifier used for data isolation.
     * @param userId User identifier to resolve.
     * @return Returns resolved contact information when available.
     */
    @Override
    public Optional<UserContact> resolve(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        if (properties.getRedisCache().isEnabled()) {
            Optional<UserContact> cached = readCache(tenantId, userId);
            if (cached.isPresent()) {
                return cached;
            }
        }

        Optional<UserContact> resolved = userServiceResolver.resolve(tenantId, userId);
        if (resolved.isEmpty() && properties.getSyntheticContacts().isEnabled()) {
            SyntheticUserContactResolver fallback = fallbackResolver.getIfAvailable();
            if (fallback == null) {
                log.error("Synthetic contacts are enabled but not available for the active profile tenantId={} userId={}", tenantId, userId);
            } else {
                resolved = fallback.resolve(tenantId, userId);
            }
        }

        if (properties.getRedisCache().isEnabled() && resolved.isPresent()) {
            writeCache(tenantId, userId, resolved.get());
        }

        return resolved;
    }

    private Optional<UserContact> readCache(Long tenantId, Long userId) {
        String key = cacheKey(tenantId, userId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, UserContact.class));
        } catch (Exception ex) {
            log.debug("Contact cache read failed key={} error={}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(Long tenantId, Long userId, UserContact contact) {
        String key = cacheKey(tenantId, userId);
        try {
            String json = objectMapper.writeValueAsString(contact);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(properties.getRedisCache().getContactTtlSeconds()));
        } catch (Exception ex) {
            log.debug("Contact cache write failed key={} error={}", key, ex.getMessage());
        }
    }

    private static String cacheKey(Long tenantId, Long userId) {
        return "notification:contact:" + tenantId + ":" + userId;
    }
}

