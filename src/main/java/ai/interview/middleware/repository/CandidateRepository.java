package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.enums.CandidateStatus;
import ai.interview.middleware.repository.projection.StatusCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByStatus(CandidateStatus status);

    /** One grouped query for the dashboard instead of one COUNT per status. */
    @Query("SELECT CAST(c.status AS string) AS name, COUNT(c) AS total FROM Candidate c GROUP BY c.status")
    List<StatusCount> countGroupedByStatus();
}
