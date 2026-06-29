package huynv.fileservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Describes the response returned when a pre-signed upload reservation is created.
 *
 * @param fileId File identifier reserved for the upload.
 * @param uploadToken Confirmation token that binds the later confirm call.
 * @param bucket Storage bucket that will receive the object.
 * @param objectKey Object key reserved for the upload.
 * @param uploadUrl Pre-signed upload URL.
 * @param requiredHeaders Headers that the client must include during upload.
 * @param expiresAt Expiration timestamp for the pre-signed upload URL.
 * @return Returns an immutable response payload for pre-signed uploads.
 */
public record PresignedUploadResponse(
        UUID fileId,
        String uploadToken,
        String bucket,
        String objectKey,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}

