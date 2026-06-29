package huynv.fileservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the control-plane response returned after initiating a multipart upload session.
 *
 * @param fileId File identifier reserved for the multipart upload.
 * @param uploadSessionId Multipart session identifier.
 * @param uploadId Native object-storage multipart upload identifier.
 * @param bucket Storage bucket name.
 * @param objectKey Stable object key for the upload target.
 * @param expiresAt Multipart session expiration timestamp.
 * @return Returns an immutable multipart upload initiation response.
 */
public record InitiateMultipartUploadResponse(
        UUID fileId,
        UUID uploadSessionId,
        String uploadId,
        String bucket,
        String objectKey,
        Instant expiresAt
) {
}

