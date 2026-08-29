package ai.interview.middleware.exception;

import ai.interview.middleware.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the downstream AI service is unreachable or returns an error. Renders as 503 so a
 * client (and any retry policy in front of it) can tell an upstream outage from a bad request.
 */
public class AiServiceException extends ApiException {

    public AiServiceException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_SERVICE_UNAVAILABLE, message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_SERVICE_UNAVAILABLE, message, cause);
    }
}
