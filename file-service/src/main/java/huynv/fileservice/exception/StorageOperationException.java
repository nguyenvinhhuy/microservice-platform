package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that object storage could not complete a requested operation safely.
 */
public class StorageOperationException extends ApiException {

    /**
     * Creates a storage-operation exception with a stable error code and detail message.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a storage-operation exception.
     */
    public StorageOperationException(String errorCode, String message) {
        super(HttpStatus.BAD_GATEWAY, errorCode, message);
    }
}

