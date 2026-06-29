package huynv.fileservice.storage;

import java.time.Instant;
import java.util.Map;

/**
 * Describes a generated pre-signed upload request for direct-to-storage client uploads.
 *
 * @param bucket Target storage bucket.
 * @param objectKey Target storage object key.
 * @param uploadUrl Pre-signed upload URL.
 * @param requiredHeaders Headers the client must send.
 * @param expiresAt Expiration timestamp for the upload URL.
 * @return Returns immutable pre-signed upload details.
 */
public record PresignedUploadDetails(
        String bucket,
        String objectKey,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}

