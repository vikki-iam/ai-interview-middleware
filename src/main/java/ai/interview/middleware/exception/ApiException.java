package ai.interview.middleware.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for failures that map to a deliberate HTTP response.
 *
 * <p>Carrying status and code on the exception keeps {@code GlobalExceptionHandler} free of a growing
 * {@code instanceof} chain: any subclass is rendered correctly without touching the handler.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
