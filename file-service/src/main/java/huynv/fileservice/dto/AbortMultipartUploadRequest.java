package huynv.fileservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Describes the client request used to abort an in-flight multipart upload.
 *
 * @param uploadId Native object-storage multipart upload identifier.
 * @return Returns an immutable request payload for multipart upload abort processing.
 */
public record AbortMultipartUploadRequest(@NotBlank String uploadId) {
}

