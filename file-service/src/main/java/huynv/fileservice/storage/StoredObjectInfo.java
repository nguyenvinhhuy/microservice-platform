package huynv.fileservice.storage;

/**
 * Describes metadata about an object stored in MinIO-compatible storage.
 *
 * @param bucket Storage bucket name.
 * @param objectKey Object key inside the bucket.
 * @param contentLength Object size in bytes.
 * @param contentType Stored MIME type when available.
 * @param eTag Storage-layer ETag when available.
 * @return Returns immutable object metadata fetched from storage.
 */
public record StoredObjectInfo(String bucket, String objectKey, long contentLength, String contentType, String eTag) {
}

