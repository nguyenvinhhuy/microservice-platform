package huynv.fileservice.service;

import huynv.fileservice.cache.FileCacheService;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.FileQuota;
import huynv.fileservice.repository.FileQuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Enforces tenant storage quotas with transactional updates and cache invalidation.
 */
@Service
public class QuotaService {

    private final FileQuotaRepository fileQuotaRepository;
    private final FileCacheService fileCacheService;
    private final FileServiceProperties properties;

    /**
     * Creates a quota service backed by the file_quota table.
     *
     * @param fileQuotaRepository Repository used to load and persist quota rows.
     * @param fileCacheService Cache service used to invalidate quota reads.
     * @param properties File-service properties containing default quota settings.
     * @return Initializes the quota service.
     */
    public QuotaService(FileQuotaRepository fileQuotaRepository, FileCacheService fileCacheService, FileServiceProperties properties) {
        this.fileQuotaRepository = Objects.requireNonNull(fileQuotaRepository, "fileQuotaRepository");
        this.fileCacheService = Objects.requireNonNull(fileCacheService, "fileCacheService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Reserves bytes against the tenant quota.
     *
     * @param tenantId Tenant identifier.
     * @param bytes Number of bytes to reserve.
     * @return Performs a side effect by updating the tenant quota row.
     */
    @Transactional
    public void reserve(UUID tenantId, long bytes) {
        FileQuota quota = fileQuotaRepository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> new FileQuota(tenantId, properties.getQuota().getDefaultQuotaBytes()));
        quota.reserve(bytes);
        fileQuotaRepository.save(quota);
        fileCacheService.evictQuota(tenantId);
    }

    /**
     * Releases bytes back to the tenant quota.
     *
     * @param tenantId Tenant identifier.
     * @param bytes Number of bytes to release.
     * @return Performs a side effect by updating the tenant quota row.
     */
    @Transactional
    public void release(UUID tenantId, long bytes) {
        FileQuota quota = fileQuotaRepository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> new FileQuota(tenantId, properties.getQuota().getDefaultQuotaBytes()));
        quota.release(bytes);
        fileQuotaRepository.save(quota);
        fileCacheService.evictQuota(tenantId);
    }
}

