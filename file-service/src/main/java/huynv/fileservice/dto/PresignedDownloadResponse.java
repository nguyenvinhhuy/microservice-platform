package huynv.fileservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the response returned when a pre-signed download URL is generated.
 *
 * @param fileId File identifier for the requested object.
 * @param downloadUrl Pre-signed download URL.
 * @param expiresAt Expiration timestamp for the pre-signed download URL.
 * @return Returns an immutable response payload for pre-signed downloads.
 */
public record PresignedDownloadResponse(UUID fileId, String downloadUrl, Instant expiresAt) {
}

