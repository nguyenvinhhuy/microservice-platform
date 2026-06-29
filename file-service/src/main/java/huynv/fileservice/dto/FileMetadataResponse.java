package huynv.fileservice.dto;

import huynv.fileservice.domain.FileStatus;
import huynv.fileservice.domain.FileVisibility;
import huynv.fileservice.domain.MalwareScanStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the external metadata view returned for a file record.
 *
 * @param id File identifier.
 * @param tenantId Tenant identifier that owns the file.
 * @param ownerUserId User identifier that owns the file.
 * @param category Business category used for routing and authorization.
 * @param bucket Storage bucket containing the object.
 * @param objectKey Object key inside the bucket.
 * @param originalFilename Original file name.
 * @param contentType MIME type stored for the object.
 * @param sizeBytes Object size in bytes.
 * @param checksumSha256 SHA-256 checksum in lowercase hexadecimal form.
 * @param status Lifecycle status of the file.
 * @param visibility Visibility mode used for authorization.
 * @param malwareScanStatus Malware scanning status.
 * @param metadataJson Optional metadata payload stored with the file record.
 * @param createdAt Creation timestamp.
 * @param updatedAt Last update timestamp.
 * @param deletedAt Soft-delete timestamp when present.
 * @return Returns an immutable metadata response for external clients.
 */
public record FileMetadataResponse(
        UUID id,
        UUID tenantId,
        UUID ownerUserId,
        String category,
        String bucket,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        FileStatus status,
        FileVisibility visibility,
        MalwareScanStatus malwareScanStatus,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}

