package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/** Thrown when a create/update would violate a uniqueness rule. Renders as 409. */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_RESOURCE,
                "%s already exists with %s '%s'".formatted(resource, field, value));
    }

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
