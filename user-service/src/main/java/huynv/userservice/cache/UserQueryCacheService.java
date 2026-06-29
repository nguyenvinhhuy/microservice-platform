package huynv.userservice.cache;

import huynv.userservice.domain.UserEntity;
import huynv.userservice.domain.UserPreferencesEntity;
import huynv.userservice.metrics.UserMetrics;
import huynv.userservice.repository.UserPreferencesRepository;
import huynv.userservice.repository.UserRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides Redis-backed tenant-aware caches for user profile and preference lookups.
 */
@Service
public class UserQueryCacheService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final CacheManager cacheManager;
    private final UserMetrics userMetrics;

    /**
     * Creates a cache-backed query service for user profiles and preferences.
     *
     * @param userRepository Repository used to load user profiles on cache misses.
     * @param userPreferencesRepository Repository used to load preferences on cache misses.
     * @param cacheManager Cache manager used to read and update Redis-backed cache regions.
     * @param userMetrics Metrics recorder used to track cache efficiency.
     * @return Initializes a cache-backed query service instance.
     */
    public UserQueryCacheService(
            UserRepository userRepository,
            UserPreferencesRepository userPreferencesRepository,
            CacheManager cacheManager,
            UserMetrics userMetrics
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.userPreferencesRepository = Objects.requireNonNull(userPreferencesRepository, "userPreferencesRepository");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
    }

    /**
     * Loads a cached user profile by tenant and domain user identifier.
     *
     * @param tenantId Tenant identifier owning the profile.
     * @param userId Domain user identifier.
     * @return Returns a cached user profile snapshot when present.
     */
    public Optional<CachedUserProfile> findProfileById(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        String key = cacheKey(tenantId, userId);
        Cache cache = requiredCache("user-profile-by-id");
        CachedUserProfile cachedUserProfile = cache.get(key, CachedUserProfile.class);
        if (cachedUserProfile != null) {
            userMetrics.recordCacheHit("user-profile-by-id");
            return Optional.of(cachedUserProfile);
        }
        userMetrics.recordCacheMiss("user-profile-by-id");
        Optional<CachedUserProfile> loaded = userRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, userId).map(CachedUserProfile::fromEntity);
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded;
    }

    /**
     * Loads a cached user profile by tenant and Keycloak subject identifier.
     *
     * @param tenantId Tenant identifier owning the profile.
     * @param keycloakUserId Keycloak subject identifier.
     * @return Returns a cached user profile snapshot when present.
     */
    public Optional<CachedUserProfile> findProfileByKeycloakUserId(UUID tenantId, UUID keycloakUserId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(keycloakUserId, "keycloakUserId");
        String key = cacheKey(tenantId, keycloakUserId);
        Cache cache = requiredCache("user-profile-by-keycloak");
        CachedUserProfile cachedUserProfile = cache.get(key, CachedUserProfile.class);
        if (cachedUserProfile != null) {
            userMetrics.recordCacheHit("user-profile-by-keycloak");
            return Optional.of(cachedUserProfile);
        }
        userMetrics.recordCacheMiss("user-profile-by-keycloak");
        Optional<CachedUserProfile> loaded = userRepository.findByTenantIdAndKeycloakUserIdAndDeletedAtIsNull(tenantId, keycloakUserId).map(CachedUserProfile::fromEntity);
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded;
    }

    /**
     * Loads cached user preferences by tenant and domain user identifier.
     *
     * @param tenantId Tenant identifier owning the preferences.
     * @param userId Domain user identifier.
     * @return Returns cached user preferences when present.
     */
    public Optional<CachedUserPreferences> findPreferences(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        String key = cacheKey(tenantId, userId);
        Cache cache = requiredCache("user-preferences");
        CachedUserPreferences cachedUserPreferences = cache.get(key, CachedUserPreferences.class);
        if (cachedUserPreferences != null) {
            userMetrics.recordCacheHit("user-preferences");
            return Optional.of(cachedUserPreferences);
        }
        userMetrics.recordCacheMiss("user-preferences");
        Optional<CachedUserPreferences> loaded = userPreferencesRepository.findByTenantIdAndUserId(tenantId, userId).map(CachedUserPreferences::fromEntity);
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded;
    }

    /**
     * Evicts cached user profile entries under both domain and Keycloak identifiers.
     *
     * @param tenantId Tenant identifier owning the profile.
     * @param userId Domain user identifier.
     * @param keycloakUserId Keycloak subject identifier.
     * @return Performs a side effect by clearing the cached profile snapshots.
     */
    public void evictUserProfile(UUID tenantId, UUID userId, UUID keycloakUserId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(keycloakUserId, "keycloakUserId");
        requiredCache("user-profile-by-id").evict(cacheKey(tenantId, userId));
        requiredCache("user-profile-by-keycloak").evict(cacheKey(tenantId, keycloakUserId));
    }

    /**
     * Evicts cached preference entries for a tenant-scoped user.
     *
     * @param tenantId Tenant identifier owning the preferences.
     * @param userId Domain user identifier.
     * @return Performs a side effect by clearing the cached preference snapshot.
     */
    public void evictPreferences(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        requiredCache("user-preferences").evict(cacheKey(tenantId, userId));
    }

    /**
     * Resolves a required cache region by name.
     *
     * @param cacheName Cache region name.
     * @return Returns the configured cache instance.
     */
    private Cache requiredCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Required cache '" + cacheName + "' is not configured.");
        }
        return cache;
    }

    /**
     * Builds a stable tenant-aware cache key.
     *
     * @param tenantId Tenant identifier owning the cached data.
     * @param scopedId Domain identifier within the tenant.
     * @return Returns a stable Redis cache key.
     */
    private String cacheKey(UUID tenantId, UUID scopedId) {
        return tenantId + ":" + scopedId;
    }

    /**
     * Represents a cache-safe snapshot of a persisted user profile.
     *
     * @param id Domain user identifier.
     * @param keycloakUserId Keycloak subject identifier.
     * @param tenantId Tenant identifier.
     * @param email Email address.
     * @param fullName Full display name.
     * @param phoneNumber Phone number.
     * @param avatarUrl Avatar URL.
     * @param status Lifecycle status.
     * @param locale Preferred locale.
     * @param timezone Preferred timezone.
     * @param createdAt Creation timestamp.
     * @param updatedAt Last update timestamp.
     * @return Returns an immutable cache-safe profile snapshot.
     */
    public record CachedUserProfile(
            UUID id,
            UUID keycloakUserId,
            UUID tenantId,
            String email,
            String fullName,
            String phoneNumber,
            String avatarUrl,
            String status,
            String locale,
            String timezone,
            Instant createdAt,
            Instant updatedAt
    ) {

        /**
         * Creates a cache-safe snapshot from a persisted user entity.
         *
         * @param userEntity Persisted user entity to copy.
         * @return Returns an immutable cache-safe snapshot.
         */
        public static CachedUserProfile fromEntity(UserEntity userEntity) {
            Objects.requireNonNull(userEntity, "userEntity");
            return new CachedUserProfile(
                    userEntity.getId(),
                    userEntity.getKeycloakUserId(),
                    userEntity.getTenantId(),
                    userEntity.getEmail(),
                    userEntity.getFullName(),
                    userEntity.getPhoneNumber(),
                    userEntity.getAvatarUrl(),
                    userEntity.getStatus().name(),
                    userEntity.getLocale(),
                    userEntity.getTimezone(),
                    userEntity.getCreatedAt(),
                    userEntity.getUpdatedAt()
            );
        }
    }

    /**
     * Represents a cache-safe snapshot of persisted user preferences.
     *
     * @param id Preference row identifier.
     * @param userId Domain user identifier.
     * @param emailEnabled Flag indicating whether email notifications are enabled.
     * @param smsEnabled Flag indicating whether SMS notifications are enabled.
     * @param pushEnabled Flag indicating whether push notifications are enabled.
     * @param marketingEnabled Flag indicating whether marketing notifications are enabled.
     * @param language Preferred language.
     * @param createdAt Creation timestamp.
     * @param updatedAt Last update timestamp.
     * @return Returns an immutable cache-safe preferences snapshot.
     */
    public record CachedUserPreferences(
            UUID id,
            UUID userId,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean pushEnabled,
            boolean marketingEnabled,
            String language,
            Instant createdAt,
            Instant updatedAt
    ) {

        /**
         * Creates a cache-safe snapshot from a persisted preferences entity.
         *
         * @param preferencesEntity Persisted preferences entity to copy.
         * @return Returns an immutable cache-safe preferences snapshot.
         */
        public static CachedUserPreferences fromEntity(UserPreferencesEntity preferencesEntity) {
            Objects.requireNonNull(preferencesEntity, "preferencesEntity");
            return new CachedUserPreferences(
                    preferencesEntity.getId(),
                    preferencesEntity.getUserId(),
                    preferencesEntity.isEmailEnabled(),
                    preferencesEntity.isSmsEnabled(),
                    preferencesEntity.isPushEnabled(),
                    preferencesEntity.isMarketingEnabled(),
                    preferencesEntity.getLanguage(),
                    preferencesEntity.getCreatedAt(),
                    preferencesEntity.getUpdatedAt()
            );
        }
    }
}

