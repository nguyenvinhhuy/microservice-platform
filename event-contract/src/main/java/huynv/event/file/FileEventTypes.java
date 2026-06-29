package huynv.event.file;

/**
 * Defines canonical Kafka event type names published or consumed by file-service.
 */
public final class FileEventTypes {

    /**
     * Prevents instantiation of the file event type constants holder.
     *
     * @return Throws an exception because this utility class must not be instantiated.
     */
    private FileEventTypes() {
        throw new UnsupportedOperationException("FileEventTypes is a utility class.");
    }

    public static final String FILE_UPLOADED_V1 = "file.uploaded.v1";
    public static final String FILE_AVAILABLE_V1 = "file.available.v1";
    public static final String FILE_DELETED_V1 = "file.deleted.v1";
    public static final String FILE_QUARANTINED_V1 = "file.quarantined.v1";
    public static final String FILE_SCAN_COMPLETED_V1 = "file.scan.completed.v1";
}

