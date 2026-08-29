package ai.interview.middleware.dto.resume;

import ai.interview.middleware.domain.enums.StorageType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Resume metadata. {@code storageKey} is intentionally omitted: it is an internal locator, and
 * exposing an S3 key invites clients to construct URLs the platform does not control.
 */
@Schema(name = "ResumeResponse", description = "Metadata for an uploaded resume")
public record ResumeResponse(
        UUID id,
        UUID candidateId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        StorageType storageType,
        @Schema(example = "/api/v1/resumes/9f1c.../download") String downloadUrl,
        Instant uploadedAt) {}
