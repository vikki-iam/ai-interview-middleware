package ai.interview.middleware.service;

import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.InterviewStatus;
import ai.interview.middleware.dto.dashboard.DashboardSummaryResponse;
import ai.interview.middleware.repository.CandidateRepository;
import ai.interview.middleware.repository.InterviewRepository;
import ai.interview.middleware.repository.InterviewResultRepository;
import ai.interview.middleware.repository.projection.StatusCount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard aggregates.
 *
 * <p>Uses {@code GROUP BY} projections rather than one COUNT per status, so the whole summary is a
 * handful of queries regardless of how many statuses exist. Read-only and side-effect free, which
 * makes it a safe endpoint to point a load test at when demonstrating HPA behaviour.
 */
@Service
public class DashboardService {

    private static final int UPCOMING_LIMIT = 5;

    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewResultRepository resultRepository;

    public DashboardService(
            CandidateRepository candidateRepository,
            InterviewRepository interviewRepository,
            InterviewResultRepository resultRepository) {
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        Map<String, Long> candidatesByStatus = toMap(candidateRepository.countGroupedByStatus());
        Map<String, Long> interviewsByStatus = toMap(interviewRepository.countGroupedByStatus());
        Map<String, Long> resultsByRecommendation = toMap(resultRepository.countGroupedByRecommendation());

        long totalCandidates = candidatesByStatus.values().stream().mapToLong(Long::longValue).sum();
        long totalInterviews = interviewsByStatus.values().stream().mapToLong(Long::longValue).sum();
        long completed = interviewsByStatus.getOrDefault(InterviewStatus.COMPLETED.name(), 0L);
        long cancelled = interviewsByStatus.getOrDefault(InterviewStatus.CANCELLED.name(), 0L);
        long pending =
                InterviewStatus.PENDING.stream()
                        .mapToLong(status -> interviewsByStatus.getOrDefault(status.name(), 0L))
                        .sum();

        BigDecimal averageScore =
                resultRepository
                        .findAverageOverallScore()
                        .map(value -> value.setScale(1, RoundingMode.HALF_UP))
                        // Null rather than 0.0: no results submitted is not the same as an average of zero.
                        .orElse(null);

        List<DashboardSummaryResponse.UpcomingInterview> upcoming =
                interviewRepository
                        .findUpcoming(InterviewStatus.PENDING, Instant.now(), PageRequest.of(0, UPCOMING_LIMIT))
                        .stream()
                        .map(this::toUpcoming)
                        .toList();

        return new DashboardSummaryResponse(
                totalCandidates,
                totalInterviews,
                completed,
                pending,
                cancelled,
                averageScore,
                candidatesByStatus,
                interviewsByStatus,
                resultsByRecommendation,
                upcoming,
                Instant.now());
    }

    private DashboardSummaryResponse.UpcomingInterview toUpcoming(Interview interview) {
        User interviewer = interview.getInterviewer();
        return new DashboardSummaryResponse.UpcomingInterview(
                interview.getId(),
                interview.getTitle(),
                interview.getCandidate().fullName(),
                interviewer == null ? null : interviewer.getFullName(),
                interview.getScheduledAt(),
                interview.getStatus().name());
    }

    /** LinkedHashMap so the JSON key order is stable across calls, which keeps diffs readable. */
    private Map<String, Long> toMap(List<StatusCount> counts) {
        return counts.stream()
                .collect(
                        Collectors.toMap(
                                StatusCount::getName,
                                StatusCount::getTotal,
                                (left, right) -> left,
                                LinkedHashMap::new));
    }
}
