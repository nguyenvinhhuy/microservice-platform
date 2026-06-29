package huynv.fileservice.domain;

/**
 * Defines the lifecycle states tracked for file metadata records.
 */
public enum FileStatus {
    PENDING_UPLOAD,
    PENDING_SCAN,
    AVAILABLE,
    QUARANTINED,
    DELETE_PENDING,
    DELETED,
    ARCHIVED,
    UPLOAD_EXPIRED,
    SCAN_FAILED
}

