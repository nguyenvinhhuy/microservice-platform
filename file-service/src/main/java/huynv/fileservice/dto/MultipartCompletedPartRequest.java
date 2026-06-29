package huynv.fileservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Describes one completed multipart upload part supplied when finalizing object assembly.
 *
 * @param partNumber Part number inside the multipart upload.
 * @param eTag Storage-reported entity tag for the uploaded part.
 * @param checksumSha256 Optional client-computed part checksum in lowercase hexadecimal form.
 * @param sizeBytes Optional uploaded part size in bytes.
 * @return Returns an immutable completed multipart part request payload.
 */
public record MultipartCompletedPartRequest(
        @Min(1) int partNumber,
        @NotBlank String eTag,
        @Pattern(regexp = "(^$)|(^[a-f0-9]{64}$)") String checksumSha256,
        @Min(1) Long sizeBytes
) {
}

