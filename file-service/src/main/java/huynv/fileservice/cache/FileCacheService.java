package huynv.fileservice.cache;

import huynv.fileservice.domain.FileQuota;
import huynv.fileservice.dto.FileMetadataResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Centralizes tenant-aware cache access for file metadata, quotas, and pre-signed upload state.
 */
@Service
public class FileCacheService {

    private final CacheManager cacheManager;

    /**
     * Creates a cache service backed by the configured Spring cache manager.
     *
     * @param cacheManager Cache manager used to resolve named cache regions.
     * @return Initializes the file cache service.
     */
    public FileCacheService(CacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
    }

    /**
     * Loads cached file metadata when available.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @return Returns cached file metadata when present.
     */
    public FileMetadataResponse getMetadata(UUID tenantId, UUID fileId) {
        Cache.ValueWrapper valueWrapper = cache("fileMetadata").get(metadataKey(tenantId, fileId));
        return valueWrapper == null ? null : (FileMetadataResponse) valueWrapper.get();
    }

    /**
     * Stores file metadata in the tenant-aware metadata cache.
     *
     * @param tenantId Tenant identifier.
     * @param response File metadata response.
     * @return Performs a side effect by updating the metadata cache.
     */
    public void putMetadata(UUID tenantId, FileMetadataResponse response) {
        cache("fileMetadata").put(metadataKey(tenantId, response.id()), response);
    }

    /**
     * Removes cached file metadata for the provided tenant and file identifier.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @return Performs a side effect by evicting the metadata cache entry.
     */
    public void evictMetadata(UUID tenantId, UUID fileId) {
        cache("fileMetadata").evict(metadataKey(tenantId, fileId));
    }

    /**
     * Loads cached quota state when available.
     *
     * @param tenantId Tenant identifier.
     * @return Returns cached quota state when present.
     */
    public FileQuota getQuota(UUID tenantId) {
        Cache.ValueWrapper valueWrapper = cache("fileQuota").get(quotaKey(tenantId));
        return valueWrapper == null ? null : (FileQuota) valueWrapper.get();
    }

    /**
     * Stores quota state in the tenant-aware quota cache.
     *
     * @param quota Persisted quota row.
     * @return Performs a side effect by updating the quota cache.
     */
    public void putQuota(FileQuota quota) {
        cache("fileQuota").put(quotaKey(quota.getTenantId()), quota);
    }

    /**
     * Removes cached quota state for the provided tenant.
     *
     * @param tenantId Tenant identifier.
     * @return Performs a side effect by evicting the quota cache entry.
     */
    public void evictQuota(UUID tenantId) {
        cache("fileQuota").evict(quotaKey(tenantId));
    }

    /**
     * Stores cached pre-signed upload state keyed by the issued upload token.
     *
     * @param uploadToken Upload token.
     * @param state Cached pre-signed upload state.
     * @return Performs a side effect by updating the pre-signed upload cache.
     */
    public void putPresignedState(String uploadToken, PresignedUploadState state) {
        cache("presignState").put(uploadToken, state);
    }

    /**
     * Loads cached pre-signed upload state when available.
     *
     * @param uploadToken Upload token.
     * @return Returns cached pre-signed upload state when present.
     */
    public PresignedUploadState getPresignedState(String uploadToken) {
        Cache.ValueWrapper valueWrapper = cache("presignState").get(uploadToken);
        return valueWrapper == null ? null : (PresignedUploadState) valueWrapper.get();
    }

    /**
     * Removes cached pre-signed upload state after successful confirmation or expiry.
     *
     * @param uploadToken Upload token.
     * @return Performs a side effect by evicting the cached upload token state.
     */
    public void evictPresignedState(String uploadToken) {
        cache("presignState").evict(uploadToken);
    }

    /**
     * Resolves a named cache and fails fast when the cache is unavailable.
     *
     * @param cacheName Cache region name.
     * @return Returns the resolved cache.
     */
    private Cache cache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache not configured: " + cacheName);
        }
        return cache;
    }

    /**
     * Builds a tenant-aware metadata cache key.
     *
     * @param tenantId Tenant identifier.
     * @param fileId File identifier.
     * @return Returns a stable metadata cache key.
     */
    private String metadataKey(UUID tenantId, UUID fileId) {
        return tenantId + ":" + fileId;
    }

    /**
     * Builds a tenant-aware quota cache key.
     *
     * @param tenantId Tenant identifier.
     * @return Returns a stable quota cache key.
     */
    private String quotaKey(UUID tenantId) {
        return tenantId.toString();
    }
}

