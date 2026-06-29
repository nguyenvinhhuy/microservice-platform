package huynv.fileservice.storage;

/**
 * Describes a completed multipart upload part used to finalize object assembly.
 *
 * @param partNumber Part number inside the multipart upload.
 * @param eTag Storage-reported entity tag for the part.
 * @return Returns immutable completed-part metadata.
 */
public record MultipartCompletedPart(int partNumber, String eTag) {
}

