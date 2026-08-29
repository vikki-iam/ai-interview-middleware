package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.enums.InterviewStatus;
import ai.interview.middleware.repository.projection.StatusCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewRepository
        extends JpaRepository<Interview, UUID>, JpaSpecificationExecutor<Interview> {

    /**
     * Fetches the associations every detail response needs, so rendering one interview is a single
     * round trip rather than N+1 lazy loads.
     */
    @EntityGraph(attributePaths = {"candidate", "interviewer", "questions"})
    Optional<Interview> findWithDetailsById(UUID id);

    long countByStatus(InterviewStatus status);

    long countByStatusIn(Collection<InterviewStatus> statuses);

    long countByCandidateId(UUID candidateId);

    @Query("SELECT CAST(i.status AS string) AS name, COUNT(i) AS total FROM Interview i GROUP BY i.status")
    List<StatusCount> countGroupedByStatus();

    @EntityGraph(attributePaths = {"candidate", "interviewer"})
    @Query("""
            SELECT i FROM Interview i
            WHERE i.status IN :statuses AND i.scheduledAt >= :from
            ORDER BY i.scheduledAt ASC
            """)
    List<Interview> findUpcoming(
            @Param("statuses") Collection<InterviewStatus> statuses,
            @Param("from") Instant from,
            Pageable pageable);
}
