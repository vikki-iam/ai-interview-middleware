package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a JWT is absent, malformed, expired, revoked or of the wrong type. Renders as 401.
 *
 * <p>The message never distinguishes "expired" from "signature invalid" to a caller, only in the
 * logs, so a token cannot be probed for validity.
 */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, message, cause);
    }
}
