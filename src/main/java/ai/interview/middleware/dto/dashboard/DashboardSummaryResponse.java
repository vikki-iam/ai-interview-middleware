package ai.interview.middleware.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "DashboardSummaryResponse", description = "Aggregated platform metrics")
public record DashboardSummaryResponse(
        @Schema(example = "6") long totalCandidates,
        @Schema(example = "6") long totalInterviews,
        @Schema(example = "2") long completedInterviews,
        @Schema(description = "SCHEDULED plus IN_PROGRESS", example = "3") long pendingInterviews,
        @Schema(example = "1") long cancelledInterviews,
        @Schema(description = "Mean overall score across all submitted results", example = "7.8")
        BigDecimal averageOverallScore,
        @Schema(description = "Candidate count by status") Map<String, Long> candidatesByStatus,
        @Schema(description = "Interview count by status") Map<String, Long> interviewsByStatus,
        @Schema(description = "Result count by recommendation") Map<String, Long> resultsByRecommendation,
        @Schema(description = "Next few interviews, soonest first") List<UpcomingInterview> upcomingInterviews,
        @Schema(description = "When these figures were computed") Instant generatedAt) {

    @Schema(name = "UpcomingInterview", description = "Condensed interview for the dashboard list")
    public record UpcomingInterview(
            UUID id,
            String title,
            String candidateName,
            String interviewerName,
            Instant scheduledAt,
            String status) {}
}
