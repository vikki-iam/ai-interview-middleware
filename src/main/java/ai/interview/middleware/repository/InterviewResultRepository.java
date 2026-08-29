package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.InterviewResult;
import ai.interview.middleware.repository.projection.StatusCount;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewResultRepository extends JpaRepository<InterviewResult, UUID> {

    Optional<InterviewResult> findByInterviewId(UUID interviewId);

    boolean existsByInterviewId(UUID interviewId);

    /**
     * Which of the given interviews already have a result.
     *
     * <p>One query for a whole page instead of an {@code EXISTS} per row; the caller must not pass an
     * empty collection because {@code IN ()} is not valid SQL.
     */
    @Query("SELECT r.interview.id FROM InterviewResult r WHERE r.interview.id IN :interviewIds")
    List<UUID> findInterviewIdsWithResults(@Param("interviewIds") Collection<UUID> interviewIds);

    @Query("SELECT AVG(r.overallScore) FROM InterviewResult r")
    Optional<BigDecimal> findAverageOverallScore();

    @Query("""
            SELECT CAST(r.recommendation AS string) AS name, COUNT(r) AS total
            FROM InterviewResult r
            GROUP BY r.recommendation
            """)
    List<StatusCount> countGroupedByRecommendation();
}
