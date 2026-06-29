package huynv.fileservice.domain;

/**
 * Defines the lifecycle states tracked for multipart upload sessions.
 */
public enum MultipartUploadStatus {
    INITIATED,
    COMPLETED,
    ABORTED,
    EXPIRED
}

