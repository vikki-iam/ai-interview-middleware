package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/** Thrown for a semantically invalid request that bean validation cannot express. Renders as 400. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
