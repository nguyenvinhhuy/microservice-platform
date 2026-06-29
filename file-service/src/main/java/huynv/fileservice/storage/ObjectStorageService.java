package huynv.fileservice.storage;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Defines the S3-compatible object storage operations required by file-service.
 */
public interface ObjectStorageService {

    /**
     * Ensures that the provided storage bucket exists before object operations are attempted.
     *
     * @param bucket Storage bucket name.
     * @return Performs a side effect by creating the bucket when it is missing.
     */
    void ensureBucketExists(String bucket);

    /**
     * Uploads a file from a temporary path into object storage.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param filePath Temporary file path to upload.
     * @param contentType MIME type to persist with the object.
     * @return Returns metadata describing the stored object.
     */
    StoredObjectInfo upload(String bucket, String objectKey, Path filePath, String contentType);

    /**
     * Loads object metadata when the object exists in storage.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @return Returns metadata describing the stored object.
     */
    StoredObjectInfo statObject(String bucket, String objectKey);

    /**
     * Generates a pre-signed upload URL for a future direct client upload.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param contentType MIME type expected for the upload.
     * @param expiresIn URL lifetime.
     * @return Returns pre-signed upload details.
     */
    PresignedUploadDetails generatePresignedUpload(String bucket, String objectKey, String contentType, Duration expiresIn);

    /**
     * Initiates a native multipart upload in object storage.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param contentType MIME type expected for the upload.
     * @return Returns the native multipart upload identifier.
     */
    MultipartUploadInitiation initiateMultipartUpload(String bucket, String objectKey, String contentType);

    /**
     * Generates a pre-signed upload URL for a single multipart upload part.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param uploadId Native multipart upload identifier.
     * @param partNumber Part number to upload.
     * @param expiresIn URL lifetime.
     * @return Returns pre-signed multipart part upload details.
     */
    PresignedMultipartUploadPartDetails generatePresignedMultipartUploadPart(String bucket, String objectKey, String uploadId, int partNumber, Duration expiresIn);

    /**
     * Completes a native multipart upload using the provided ordered list of completed parts.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param uploadId Native multipart upload identifier.
     * @param parts Ordered list of completed parts.
     * @return Returns metadata describing the completed stored object.
     */
    StoredObjectInfo completeMultipartUpload(String bucket, String objectKey, String uploadId, java.util.List<MultipartCompletedPart> parts);

    /**
     * Aborts a native multipart upload and discards any uploaded parts.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param uploadId Native multipart upload identifier.
     * @return Performs a side effect by aborting the multipart upload.
     */
    void abortMultipartUpload(String bucket, String objectKey, String uploadId);

    /**
     * Generates a pre-signed download URL for a future client read.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @param expiresIn URL lifetime.
     * @return Returns pre-signed download details.
     */
    PresignedDownloadDetails generatePresignedDownload(String bucket, String objectKey, Duration expiresIn);

    /**
     * Streams an object download from storage.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @return Returns streaming download details for the object.
     */
    DownloadedObject download(String bucket, String objectKey);

    /**
     * Deletes an object from storage when it exists.
     *
     * @param bucket Storage bucket name.
     * @param objectKey Target object key.
     * @return Performs a side effect by deleting the object when present.
     */
    void deleteObject(String bucket, String objectKey);
}

