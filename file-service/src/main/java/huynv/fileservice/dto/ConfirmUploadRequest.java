package huynv.fileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Describes the client request used to confirm that a pre-signed upload finished successfully.
 *
 * @param uploadToken Token issued during pre-signed reservation and required to bind confirmation safely.
 * @param checksumSha256 SHA-256 checksum observed by the client after upload.
 * @param metadataJson Optional replacement metadata payload for the confirmed file record.
 * @return Returns an immutable request payload for upload confirmation.
 */
public record ConfirmUploadRequest(
        @NotBlank @Size(max = 200) String uploadToken,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String checksumSha256,
        @Size(max = 4000) String metadataJson
) {
}

