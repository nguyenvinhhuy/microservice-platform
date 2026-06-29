package huynv.fileservice.storage;

/**
 * Describes the native multipart upload identifier returned by object storage.
 *
 * @param uploadId Native multipart upload identifier.
 * @return Returns immutable multipart upload initiation details.
 */
public record MultipartUploadInitiation(String uploadId) {
}

