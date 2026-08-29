package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.ExperienceLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InterviewCreateRequest", description = "Schedules an interview for a candidate")
public record InterviewCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "candidateId is required")
        UUID candidateId,

        @Schema(description = "Assign now, or leave null and use PATCH /interviews/{id}/interviewer")
        UUID interviewerId,

        @Schema(example = "DevOps Round 1 - Kubernetes Deep Dive", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "title is required")
        @Size(max = 180)
        String title,

        @Schema(example = "Senior DevOps Engineer", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "roleTitle is required")
        @Size(max = 150)
        String roleTitle,

        @Schema(example = "SENIOR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "experienceLevel is required")
        ExperienceLevel experienceLevel,

        @Schema(example = "1", defaultValue = "1")
        @Min(value = 1, message = "roundNumber must be at least 1")
        @Max(value = 10, message = "roundNumber cannot exceed 10")
        Integer roundNumber,

        @Schema(example = "2026-08-12T09:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "scheduledAt is required")
        Instant scheduledAt,

        @Schema(example = "60", defaultValue = "60")
        @Min(value = 15, message = "durationMinutes must be at least 15")
        @Max(value = 480, message = "durationMinutes cannot exceed 480")
        Integer durationMinutes,

        @Schema(example = "[\"Kubernetes\",\"Helm\",\"EKS\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "at least one focus skill is required")
        @Size(max = 20, message = "no more than 20 focus skills")
        List<@NotBlank @Size(max = 60) String> focusSkills) {}
