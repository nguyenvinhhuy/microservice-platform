package huynv.fileservice.cache;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Describes cached state required to safely confirm a pre-signed upload.
 *
 * @param fileId File identifier reserved for the upload.
 * @param tenantId Tenant identifier that owns the file.
 * @param bucket Storage bucket receiving the object.
 * @param objectKey Storage object key reserved for the upload.
 * @param expectedChecksumSha256 Expected checksum in lowercase hexadecimal form.
 * @param expiresAt Upload token expiration timestamp.
 * @return Returns immutable cached pre-signed upload state.
 */
public record PresignedUploadState(
        UUID fileId,
        UUID tenantId,
        String bucket,
        String objectKey,
        String expectedChecksumSha256,
        Instant expiresAt
) implements Serializable {
}

