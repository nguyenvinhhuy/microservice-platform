package huynv.fileservice.dto;

import huynv.fileservice.domain.FileVisibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Describes the client request used to reserve metadata and generate a pre-signed upload URL.
 *
 * @param category Business category used for routing and authorization.
 * @param originalFilename Original client-visible file name.
 * @param contentType MIME type expected for the upload.
 * @param sizeBytes Expected object size in bytes.
 * @param checksumSha256 Expected SHA-256 checksum in lowercase hexadecimal form.
 * @param visibility Requested visibility mode for subsequent reads.
 * @param metadataJson Optional metadata payload stored with the file record.
 * @return Returns an immutable request payload for pre-signed upload reservation.
 */
public record PresignedUploadRequest(
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 255) String contentType,
        @Min(1) long sizeBytes,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String checksumSha256,
        @NotNull FileVisibility visibility,
        @Size(max = 4000) String metadataJson
) {
}

