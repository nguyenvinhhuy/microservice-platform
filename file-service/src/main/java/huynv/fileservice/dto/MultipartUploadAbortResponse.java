package huynv.fileservice.dto;

import huynv.fileservice.domain.MultipartUploadStatus;
import java.util.UUID;

/**
 * Describes the control-plane response returned after aborting a multipart upload.
 *
 * @param fileId File identifier reserved for the multipart upload.
 * @param uploadSessionId Multipart session identifier.
 * @param uploadId Native multipart upload identifier.
 * @param status Final multipart session status.
 * @return Returns an immutable multipart upload abort response.
 */
public record MultipartUploadAbortResponse(
        UUID fileId,
        UUID uploadSessionId,
        String uploadId,
        MultipartUploadStatus status
) {
}

