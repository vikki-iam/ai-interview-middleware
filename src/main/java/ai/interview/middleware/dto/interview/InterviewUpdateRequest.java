package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.ExperienceLevel;
import ai.interview.middleware.domain.enums.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Full replacement of an interview's mutable fields.
 *
 * <p>{@code candidateId} is absent by design: moving an interview to a different candidate would
 * orphan its questions and result, so that is a delete-and-recreate operation.
 */
@Schema(name = "InterviewUpdateRequest", description = "Updates an existing interview")
public record InterviewUpdateRequest(
        @NotBlank(message = "title is required") @Size(max = 180) String title,

        @NotBlank(message = "roleTitle is required") @Size(max = 150) String roleTitle,

        @NotNull(message = "experienceLevel is required") ExperienceLevel experienceLevel,

        @Min(1) @Max(10) Integer roundNumber,

        @NotNull(message = "scheduledAt is required") Instant scheduledAt,

        @Min(15) @Max(480) Integer durationMinutes,

        @NotEmpty(message = "at least one focus skill is required")
        @Size(max = 20)
        List<@NotBlank @Size(max = 60) String> focusSkills,

        @Schema(description = "Optional status transition; must be legal for the current status")
        InterviewStatus status) {}
