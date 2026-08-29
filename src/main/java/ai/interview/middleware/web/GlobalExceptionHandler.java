package ai.interview.middleware.web;

import ai.interview.middleware.common.ErrorCode;
import ai.interview.middleware.common.ErrorResponse;
import ai.interview.middleware.exception.ApiException;
import ai.interview.middleware.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into the single {@link ErrorResponse} shape.
 *
 * <p>Two rules govern this class. Expected failures are logged at WARN with their message only,
 * because a 404 is not an incident and a stack trace per bad request is noise. Unexpected failures are
 * logged at ERROR with the full stack trace but answered with a generic message, so an internal
 * detail (a SQL fragment, a class name, an upstream URL) never reaches a client.
 *
 * <p>Every response carries the request id, which is the link between what the user saw and what the
 * logs recorded.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * All deliberate application failures. Status and code come from the exception, so adding a new
     * {@link ApiException} subclass needs no change here.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception, HttpServletRequest request) {

        if (exception.getStatus().is5xxServerError()) {
            log.error("{} at {}: {}", exception.getCode(), request.getRequestURI(), exception.getMessage(), exception);
        } else {
            log.warn("{} at {}: {}", exception.getCode(), request.getRequestURI(), exception.getMessage());
        }
        return build(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    // ---------------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------------

    /** Request body failed bean validation. Returns every field error at once, not just the first. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<ErrorResponse.FieldViolation> violations = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            violations.add(
                    new ErrorResponse.FieldViolation(
                            fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getRejectedValue()));
        }
        exception
                .getBindingResult()
                .getGlobalErrors()
                .forEach(
                        globalError ->
                                violations.add(
                                        new ErrorResponse.FieldViolation(
                                                globalError.getObjectName(), globalError.getDefaultMessage(), null)));

        log.warn("Validation failed at {}: {} field error(s)", request.getRequestURI(), violations.size());
        return ResponseEntity.badRequest()
                .body(
                        ErrorResponse.withFieldErrors(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                request.getRequestURI(),
                                requestId(),
                                violations));
    }

    /** Query parameter or path variable failed a constraint (Spring 6.1 method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {

        List<ErrorResponse.FieldViolation> violations = new ArrayList<>();
        exception
                .getAllValidationResults()
                .forEach(
                        result ->
                                result
                                        .getResolvableErrors()
                                        .forEach(
                                                error ->
                                                        violations.add(
                                                                new ErrorResponse.FieldViolation(
                                                                        result.getMethodParameter().getParameterName(),
                                                                        error.getDefaultMessage(),
                                                                        null))));

        log.warn("Parameter validation failed at {}", request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(
                        ErrorResponse.withFieldErrors(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ErrorCode.VALIDATION_FAILED,
                                "Request parameter validation failed",
                                request.getRequestURI(),
                                requestId(),
                                violations));
    }

    /** Bean validation raised outside the web layer, for example on a service method argument. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {

        List<ErrorResponse.FieldViolation> violations =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new ErrorResponse.FieldViolation(
                                                lastPathNode(violation), violation.getMessage(), violation.getInvalidValue()))
                        .toList();

        log.warn("Constraint violation at {}: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(
                        ErrorResponse.withFieldErrors(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                request.getRequestURI(),
                                requestId(),
                                violations));
    }

    // ---------------------------------------------------------------------------------------------
    // Malformed requests
    // ---------------------------------------------------------------------------------------------

    /**
     * Unparseable body: bad JSON, or a value that cannot become the target type (an unknown enum
     * constant, a malformed timestamp). The exception message is not echoed because it can contain the
     * raw payload.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        log.warn("Unreadable request body at {}: {}", request.getRequestURI(), exception.getMessage());
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or not valid JSON for this endpoint",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {

        String expected =
                exception.getRequiredType() == null ? "the expected type" : exception.getRequiredType().getSimpleName();
        log.warn("Type mismatch for '{}' at {}", exception.getName(), request.getRequestURI());
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "Parameter '%s' is not a valid %s".formatted(exception.getName(), expected),
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "Required parameter '%s' is missing".formatted(exception.getParameterName()),
                request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception, HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "Multipart part '%s' is missing".formatted(exception.getRequestPartName()),
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {

        log.warn("Upload rejected at {}: exceeds the configured multipart limit", request.getRequestURI());
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.PAYLOAD_TOO_LARGE,
                "The uploaded file exceeds the maximum permitted size",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {

        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "%s is not supported for this endpoint".formatted(exception.getMethod()),
                request);
    }

    /**
     * An unmapped path. Both types are handled because Spring Boot 3 raises
     * {@code NoResourceFoundException} for static-resource misses and {@code NoHandlerFoundException}
     * only when {@code throw-exception-if-no-handler-found} is enabled.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(
            Exception exception, HttpServletRequest request) {

        return build(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint %s %s".formatted(request.getMethod(), request.getRequestURI()),
                request);
    }

    // ---------------------------------------------------------------------------------------------
    // Security
    // ---------------------------------------------------------------------------------------------

    /**
     * Thrown by {@code @PreAuthorize} and by service-level ownership checks. Security-filter
     * rejections are handled earlier by {@code RestAccessDeniedHandler}; this covers the rest.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {

        log.warn("Access denied at {}: {}", request.getRequestURI(), exception.getMessage());
        return build(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action",
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {

        log.warn("Authentication failed at {}", request.getRequestURI());
        return build(
                HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, "Authentication failed", request);
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    /**
     * A unique or foreign-key constraint fired.
     *
     * <p>Services check the common cases up front to produce a specific message; reaching here means a
     * race or a case not anticipated. The database message is logged but never returned, because it
     * exposes table, column and index names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        log.error("Data integrity violation at {}", request.getRequestURI(), exception);
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                "The request conflicts with existing data",
                request);
    }

    /** Two callers updated the same row concurrently; the loser is asked to retry with fresh data. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException exception, HttpServletRequest request) {

        log.warn("Optimistic lock failure at {}: {}", request.getRequestURI(), exception.getMessage());
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                "This record was modified by someone else. Reload and try again.",
                request);
    }

    // ---------------------------------------------------------------------------------------------
    // Fallback
    // ---------------------------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {

        // The full detail goes to the logs, tied to the request id echoed to the caller.
        log.error(
                "Unhandled {} at {} {}",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting this.",
                request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request) {

        return ResponseEntity.status(status)
                .body(
                        ErrorResponse.of(
                                status.value(),
                                status.getReasonPhrase(),
                                code,
                                message,
                                request.getRequestURI(),
                                requestId()));
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
