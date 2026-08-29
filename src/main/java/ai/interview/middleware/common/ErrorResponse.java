package ai.interview.middleware.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint.
 *
 * <p>{@code code} is a stable machine-readable token; {@code message} is human-facing and safe to
 * display. Internal details (stack traces, SQL, upstream response bodies) are logged with the
 * {@code requestId} and never serialised, so a client cannot fingerprint the internals.
 */
@Schema(name = "ErrorResponse", description = "Standard error payload")
public record ErrorResponse(
        @Schema(example = "2026-08-04T10:15:30Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "Not Found") String error,
        @Schema(example = "RESOURCE_NOT_FOUND") String code,
        @Schema(example = "Candidate not found: 9f1c...") String message,
        @Schema(example = "/api/v1/candidates/9f1c...") String path,
        @Schema(description = "Correlates this response with the server logs") String requestId,
        @Schema(description = "Per-field validation failures; null unless the request failed validation")
        List<FieldViolation> fieldErrors) {

    @Schema(name = "FieldViolation", description = "A single field-level validation failure")
    public record FieldViolation(
            @Schema(example = "email") String field,
            @Schema(example = "must be a well-formed email address") String message,
            @Schema(example = "not-an-email") Object rejectedValue) {}

    public static ErrorResponse of(
            int status, String error, String code, String message, String path, String requestId) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, requestId, null);
    }

    public static ErrorResponse withFieldErrors(
            int status,
            String error,
            String code,
            String message,
            String path,
            String requestId,
            List<FieldViolation> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, requestId, fieldErrors);
    }
}
