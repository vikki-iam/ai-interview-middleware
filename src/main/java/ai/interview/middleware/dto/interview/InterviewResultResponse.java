package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.Recommendation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "InterviewResultResponse", description = "An interview scorecard")
public record InterviewResultResponse(
        UUID id,
        UUID interviewId,
        BigDecimal technicalScore,
        BigDecimal communicationScore,
        BigDecimal problemSolvingScore,
        @Schema(description = "Server-derived mean of the three dimension scores") BigDecimal overallScore,
        Recommendation recommendation,
        String strengths,
        String improvements,
        String feedback,
        UUID submittedById,
        String submittedByName,
        Instant submittedAt) {}
