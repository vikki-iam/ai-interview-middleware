package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "InterviewStatusUpdateRequest", description = "Moves an interview to a new status")
public record InterviewStatusUpdateRequest(
        @Schema(
                example = "IN_PROGRESS",
                description = "Must be a legal transition from the current status",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "status is required")
        InterviewStatus status) {}
