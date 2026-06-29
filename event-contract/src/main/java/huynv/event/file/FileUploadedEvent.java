package huynv.event.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a file object that finished client upload and is awaiting scan or downstream processing.
 *
 * @param fileId File identifier owned by file-service.
 * @param tenantId Tenant identifier that owns the file.
 * @param ownerUserId User identifier that initiated the upload.
 * @param category Business category used for authorization and object-key partitioning.
 * @param bucket Storage bucket that currently contains the uploaded object.
 * @param objectKey Object key inside the storage bucket.
 * @param originalFilename Original client-visible file name.
 * @param contentType MIME type accepted by validation.
 * @param sizeBytes Stored object size in bytes.
 * @param checksumSha256 SHA-256 checksum of the uploaded object.
 * @param visibility Visibility mode that governs read authorization.
 * @param uploadedAt Timestamp when the upload became durable.
 * @return Returns an immutable payload describing a newly uploaded file.
 */
public record FileUploadedEvent(
        UUID fileId,
        UUID tenantId,
        UUID ownerUserId,
        String category,
        String bucket,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String visibility,
        Instant uploadedAt
) {
}

