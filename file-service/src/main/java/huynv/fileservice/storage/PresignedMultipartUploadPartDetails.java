package huynv.fileservice.storage;

import java.time.Instant;
import java.util.Map;

/**
 * Describes the pre-signed upload details returned for a single multipart upload part.
 *
 * @param bucket Storage bucket name.
 * @param objectKey Target object key.
 * @param uploadId Native multipart upload identifier.
 * @param partNumber Part number to upload.
 * @param uploadUrl Pre-signed URL for the part upload.
 * @param requiredHeaders Required headers that must accompany the upload request.
 * @param expiresAt Expiration timestamp for the pre-signed URL.
 * @return Returns immutable multipart part upload details.
 */
public record PresignedMultipartUploadPartDetails(
        String bucket,
        String objectKey,
        String uploadId,
        int partNumber,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}

