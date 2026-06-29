package huynv.fileservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Describes the client request used to obtain a pre-signed URL for a multipart upload part.
 *
 * @param uploadId Native object-storage multipart upload identifier.
 * @param checksumSha256 Optional client-computed part checksum in lowercase hexadecimal form.
 * @param sizeBytes Optional part size in bytes for audit and validation use.
 * @return Returns an immutable request payload for multipart part URL issuance.
 */
public record PresignMultipartUploadPartRequest(
        @NotBlank String uploadId,
        @Pattern(regexp = "(^$)|(^[a-f0-9]{64}$)") String checksumSha256,
        @Min(1) @Max(Integer.MAX_VALUE) long sizeBytes
) {
}

