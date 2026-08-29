package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.ExperienceLevel;
import ai.interview.middleware.domain.enums.InterviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InterviewResponse", description = "An interview with its participants")
public record InterviewResponse(
        UUID id,
        UUID candidateId,
        @Schema(example = "Neha Gupta") String candidateName,
        @Schema(example = "neha.gupta@example.com") String candidateEmail,
        UUID interviewerId,
        @Schema(example = "Priya Sharma") String interviewerName,
        String title,
        String roleTitle,
        ExperienceLevel experienceLevel,
        int roundNumber,
        Instant scheduledAt,
        int durationMinutes,
        InterviewStatus status,
        List<String> focusSkills,
        @Schema(description = "Questions; present only on GET /interviews/{id}")
        List<InterviewQuestionResponse> questions,
        @Schema(description = "True once a result has been submitted") boolean resultSubmitted,
        Instant createdAt,
        Instant updatedAt) {}
