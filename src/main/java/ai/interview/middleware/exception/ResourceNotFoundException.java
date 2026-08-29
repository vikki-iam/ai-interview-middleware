package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/** Thrown when a referenced entity does not exist. Renders as 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "%s not found: %s".formatted(resource, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
