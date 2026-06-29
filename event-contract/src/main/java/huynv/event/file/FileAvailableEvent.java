package huynv.event.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a file object that passed malware scanning and is now available for reads.
 *
 * @param fileId File identifier owned by file-service.
 * @param tenantId Tenant identifier that owns the file.
 * @param ownerUserId User identifier that owns the file.
 * @param category Business category used for routing and policies.
 * @param bucket Storage bucket containing the object.
 * @param objectKey Stable object key inside the bucket.
 * @param contentType MIME type served to clients.
 * @param sizeBytes Stored object size in bytes.
 * @param checksumSha256 SHA-256 checksum of the stored object.
 * @param visibility Visibility mode that governs read authorization.
 * @param availableAt Timestamp when the file became readable.
 * @return Returns an immutable payload describing an available file.
 */
public record FileAvailableEvent(
        UUID fileId,
        UUID tenantId,
        UUID ownerUserId,
        String category,
        String bucket,
        String objectKey,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String visibility,
        Instant availableAt
) {
}

