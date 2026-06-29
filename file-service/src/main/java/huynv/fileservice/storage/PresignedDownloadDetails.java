package huynv.fileservice.storage;

import java.time.Instant;

/**
 * Describes a generated pre-signed download request for client reads.
 *
 * @param bucket Storage bucket containing the object.
 * @param objectKey Storage object key.
 * @param downloadUrl Pre-signed download URL.
 * @param expiresAt Expiration timestamp for the download URL.
 * @return Returns immutable pre-signed download details.
 */
public record PresignedDownloadDetails(String bucket, String objectKey, String downloadUrl, Instant expiresAt) {
}

