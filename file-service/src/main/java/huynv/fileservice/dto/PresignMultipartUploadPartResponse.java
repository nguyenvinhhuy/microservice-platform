package huynv.fileservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Describes the response returned after issuing a multipart upload part URL.
 *
 * @param fileId File identifier reserved for the multipart upload.
 * @param uploadSessionId Multipart session identifier.
 * @param uploadId Native multipart upload identifier.
 * @param partNumber Part number to upload.
 * @param uploadUrl Pre-signed URL for the part upload.
 * @param requiredHeaders Required headers that must accompany the upload request.
 * @param expiresAt Expiration timestamp for the pre-signed URL.
 * @return Returns an immutable multipart part upload response.
 */
public record PresignMultipartUploadPartResponse(
        UUID fileId,
        UUID uploadSessionId,
        String uploadId,
        int partNumber,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}

