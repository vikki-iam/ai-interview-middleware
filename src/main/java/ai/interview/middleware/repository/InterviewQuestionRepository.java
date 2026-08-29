package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.InterviewQuestion;
import ai.interview.middleware.domain.enums.QuestionSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {

    List<InterviewQuestion> findByInterviewIdOrderBySequenceNoAsc(UUID interviewId);

    long countByInterviewId(UUID interviewId);

    /**
     * Regenerating an AI question set replaces only the AI-sourced rows; questions an interviewer
     * typed by hand survive.
     *
     * <p>{@code flushAutomatically} makes the delete reach the database before {@link
     * #findMaxSequenceNo} computes the next sequence, and {@code clearAutomatically} evicts the
     * removed rows so the persistence context cannot resurrect them on the next flush.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM InterviewQuestion q WHERE q.interview.id = :interviewId AND q.source = :source")
    int deleteByInterviewIdAndSource(
            @Param("interviewId") UUID interviewId, @Param("source") QuestionSource source);

    @Query("SELECT COALESCE(MAX(q.sequenceNo), 0) FROM InterviewQuestion q WHERE q.interview.id = :interviewId")
    int findMaxSequenceNo(@Param("interviewId") UUID interviewId);
}
