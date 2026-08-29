package ai.interview.middleware.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "AssignInterviewerRequest", description = "Assigns or reassigns the interviewer")
public record AssignInterviewerRequest(
        @Schema(
                description = "Must reference a user with the INTERVIEWER or ADMIN role",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "interviewerId is required")
        UUID interviewerId) {}
