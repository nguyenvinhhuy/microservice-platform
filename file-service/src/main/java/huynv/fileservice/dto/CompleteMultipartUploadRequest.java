package huynv.fileservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Describes the client request used to complete a multipart upload.
 *
 * @param uploadId Native object-storage multipart upload identifier.
 * @param checksumSha256 Full-object SHA-256 checksum observed by the client after upload completion.
 * @param metadataJson Optional replacement metadata payload for the file record.
 * @param parts Ordered list of completed multipart parts.
 * @return Returns an immutable request payload for multipart upload completion.
 */
public record CompleteMultipartUploadRequest(
        @NotBlank String uploadId,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String checksumSha256,
        @Size(max = 4000) String metadataJson,
        @NotEmpty List<@Valid MultipartCompletedPartRequest> parts
) {
}

