package ai.interview.middleware.service;

import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.entity.InterviewQuestion;
import ai.interview.middleware.domain.enums.Difficulty;
import ai.interview.middleware.domain.enums.QuestionSource;
import ai.interview.middleware.repository.InterviewQuestionRepository;
import ai.interview.middleware.repository.InterviewRepository;
import ai.interview.middleware.service.ai.AiQuestionSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a generated question set.
 *
 * <p>A separate collaborator so the AI call in {@code InterviewService} happens outside any
 * transaction. Wrapping a 45-second HTTP call in {@code @Transactional} would pin a connection from a
 * 10-connection pool for its duration, and a slow AI service would exhaust the pool and take the rest
 * of the API down with it.
 */
@Service
public class InterviewQuestionWriter {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionWriter.class);

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;

    public InterviewQuestionWriter(
            InterviewRepository interviewRepository, InterviewQuestionRepository questionRepository) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Writes the generated questions and returns the interview's full, ordered question list.
     *
     * @param replaceExisting when true, previously AI-generated questions are removed first;
     *     interviewer-authored ({@code MANUAL}) questions are always kept
     */
    @Transactional
    public List<InterviewQuestion> persist(
            UUID interviewId, AiQuestionSet generated, boolean replaceExisting) {

        if (replaceExisting) {
            int removed =
                    questionRepository.deleteByInterviewIdAndSource(interviewId, QuestionSource.AI);
            log.debug("Removed {} previously generated question(s) from interview {}", removed, interviewId);
        }

        // Appending after the current maximum keeps uq_interview_questions_sequence satisfied without
        // renumbering surviving rows. Gaps in the sequence are harmless: it is an ordering key, not a
        // count.
        int nextSequence = questionRepository.findMaxSequenceNo(interviewId) + 1;

        // A lazy reference is enough to set the foreign key and avoids re-selecting the interview.
        Interview interviewRef = interviewRepository.getReferenceById(interviewId);

        List<InterviewQuestion> toSave = new ArrayList<>();
        for (AiQuestionSet.AiQuestion source : generated.questions()) {
            InterviewQuestion question = new InterviewQuestion();
            question.setInterview(interviewRef);
            question.setSequenceNo(nextSequence++);
            question.setQuestionText(source.questionText().trim());
            question.setCategory(truncate(defaultIfBlank(source.category(), "General"), 60));
            question.setDifficulty(parseDifficulty(source.difficulty()));
            question.setExpectedAnswer(mergeAnswerAndHint(source));
            question.setSource(QuestionSource.AI);
            question.setExternalSetId(generated.setId());
            toSave.add(question);
        }

        questionRepository.saveAll(toSave);
        log.info(
                "Persisted {} AI question(s) for interview {} from set {}",
                toSave.size(),
                interviewId,
                generated.setId());

        return questionRepository.findByInterviewIdOrderBySequenceNoAsc(interviewId).stream()
                .sorted(Comparator.comparingInt(InterviewQuestion::getSequenceNo))
                .toList();
    }

    /**
     * The AI service returns a model answer and a separate hint on what to listen for. Both are
     * interviewer-only, so they are stored in one field rather than adding a column that would always
     * be shown or hidden together with the other.
     */
    private String mergeAnswerAndHint(AiQuestionSet.AiQuestion source) {
        boolean hasAnswer = source.expectedAnswer() != null && !source.expectedAnswer().isBlank();
        boolean hasHint = source.evaluationHint() != null && !source.evaluationHint().isBlank();
        if (hasAnswer && hasHint) {
            return source.expectedAnswer().trim() + "\n\nWhat to listen for: " + source.evaluationHint().trim();
        }
        if (hasAnswer) {
            return source.expectedAnswer().trim();
        }
        return hasHint ? "What to listen for: " + source.evaluationHint().trim() : null;
    }

    private Difficulty parseDifficulty(String raw) {
        if (raw == null) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // A generative model can return anything. An unexpected label is not worth failing the
            // whole request over, so it degrades to MEDIUM and is logged.
            log.warn("Unrecognised difficulty '{}' from the AI service; defaulting to MEDIUM", raw);
            return Difficulty.MEDIUM;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
