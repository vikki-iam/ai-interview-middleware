package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/** Thrown when the resume storage backend (local disk or S3) fails. Renders as 500. */
public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.STORAGE_FAILURE, message, cause);
    }

    public StorageException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.STORAGE_FAILURE, message);
    }
}
