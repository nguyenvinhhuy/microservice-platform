package huynv.fileservice.service;

import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.exception.TooManyRequestsException;
import huynv.fileservice.metrics.FileMetrics;
import huynv.fileservice.security.AuthenticatedUser;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * Enforces Redis-backed distributed rate limits for upload, download, and pre-signed URL abuse protection.
 */
@Service
public class DistributedRateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final FileServiceProperties properties;
    private final FileMetrics fileMetrics;

    /**
     * Creates a distributed rate-limit service backed by Redis counters.
     *
     * @param stringRedisTemplate Redis template used to increment windowed counters.
     * @param properties File-service properties containing rate-limit settings.
     * @param fileMetrics Metrics recorder used for rejected requests.
     * @return Initializes the distributed rate-limit service.
     */
    public DistributedRateLimitService(StringRedisTemplate stringRedisTemplate, FileServiceProperties properties, FileMetrics fileMetrics) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.fileMetrics = Objects.requireNonNull(fileMetrics, "fileMetrics");
    }

    /**
     * Checks upload request limits for the current tenant user and client IP.
     *
     * @param user Authenticated tenant user.
     * @param clientIp Remote client IP address.
     * @return Performs a side effect by throwing when limits are exceeded.
     */
    public void checkUpload(AuthenticatedUser user, String clientIp) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        enforce("upload-user", subject(user, clientIp), properties.getRateLimit().getUploadRequestsPerMinute(), Duration.ofMinutes(1));
        enforce("upload-ip", normalizeIp(clientIp), properties.getRateLimit().getIpBurstPerMinute(), Duration.ofMinutes(1));
    }

    /**
     * Checks download request limits for the current tenant user and client IP.
     *
     * @param subject Tenant-user subject string.
     * @param clientIp Remote client IP address.
     * @return Performs a side effect by throwing when limits are exceeded.
     */
    public void checkDownload(String subject, String clientIp) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        enforce("download-subject", subject, properties.getRateLimit().getDownloadRequestsPerMinute(), Duration.ofMinutes(1));
        enforce("download-ip", normalizeIp(clientIp), properties.getRateLimit().getIpBurstPerMinute(), Duration.ofMinutes(1));
    }

    /**
     * Checks pre-signed URL issuance limits for the current tenant user and client IP.
     *
     * @param user Authenticated tenant user.
     * @param clientIp Remote client IP address.
     * @return Performs a side effect by throwing when limits are exceeded.
     */
    public void checkPresign(AuthenticatedUser user, String clientIp) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        enforce("presign-user", subject(user, clientIp), properties.getRateLimit().getPresignRequestsPerMinute(), Duration.ofMinutes(1));
        enforce("presign-ip", normalizeIp(clientIp), properties.getRateLimit().getIpBurstPerMinute(), Duration.ofMinutes(1));
    }

    /**
     * Builds a tenant-aware subject key for rate limiting.
     *
     * @param user Authenticated tenant user.
     * @param clientIp Remote client IP address.
     * @return Returns a stable rate-limit subject key.
     */
    public String subject(AuthenticatedUser user, String clientIp) {
        return user.tenantId() + ":" + user.userId() + ":" + normalizeIp(clientIp);
    }

    private void enforce(String bucket, String subject, int limit, Duration window) {
        String key = "file-service:rate-limit:" + bucket + ":" + subject;
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(key, window);
        }
        if (current != null && current > limit) {
            fileMetrics.recordRateLimitRejection(bucket);
            throw new TooManyRequestsException("RATE_LIMIT_EXCEEDED", "The request rate limit has been exceeded for " + bucket + ".");
        }
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim().replace(':', '_');
    }
}

