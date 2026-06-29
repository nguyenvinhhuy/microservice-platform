package huynv.fileservice.storage;

import java.io.InputStream;

/**
 * Describes a streaming object download returned by the storage abstraction.
 *
 * @param inputStream Streaming input for the object bytes.
 * @param contentLength Object size in bytes.
 * @param contentType Stored MIME type when available.
 * @return Returns immutable streaming download details.
 */
public record DownloadedObject(InputStream inputStream, long contentLength, String contentType) {
}

