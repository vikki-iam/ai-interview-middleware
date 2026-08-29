package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.Resume;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    @EntityGraph(attributePaths = {"candidate"})
    Optional<Resume> findWithCandidateById(UUID id);

    long countByCandidateId(UUID candidateId);
}
