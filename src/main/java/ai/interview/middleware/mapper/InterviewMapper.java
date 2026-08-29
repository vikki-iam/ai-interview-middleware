package ai.interview.middleware.mapper;

import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.entity.InterviewQuestion;
import ai.interview.middleware.domain.entity.InterviewResult;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.dto.interview.InterviewQuestionResponse;
import ai.interview.middleware.dto.interview.InterviewResponse;
import ai.interview.middleware.dto.interview.InterviewResultResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InterviewMapper {

    /** Summary view for list endpoints: no questions, so no extra query per row. */
    public InterviewResponse toSummaryResponse(Interview interview, boolean resultSubmitted) {
        return build(interview, null, resultSubmitted);
    }

    /**
     * Detail view.
     *
     * @param includeExpectedAnswers false for CANDIDATE callers, who may see the questions on their
     *     own interview but not the model answers.
     */
    public InterviewResponse toDetailResponse(
            Interview interview, boolean includeExpectedAnswers, boolean resultSubmitted) {
        List<InterviewQuestionResponse> questions =
                interview.getQuestions().stream()
                        .map(question -> toQuestionResponse(question, includeExpectedAnswers))
                        .toList();
        return build(interview, questions, resultSubmitted);
    }

    public InterviewQuestionResponse toQuestionResponse(
            InterviewQuestion question, boolean includeExpectedAnswer) {
        return new InterviewQuestionResponse(
                question.getId(),
                question.getSequenceNo(),
                question.getQuestionText(),
                question.getCategory(),
                question.getDifficulty(),
                includeExpectedAnswer ? question.getExpectedAnswer() : null,
                question.getSource(),
                question.getExternalSetId());
    }

    public InterviewResultResponse toResultResponse(InterviewResult result) {
        User submitter = result.getSubmittedBy();
        return new InterviewResultResponse(
                result.getId(),
                result.getInterview().getId(),
                result.getTechnicalScore(),
                result.getCommunicationScore(),
                result.getProblemSolvingScore(),
                result.getOverallScore(),
                result.getRecommendation(),
                result.getStrengths(),
                result.getImprovements(),
                result.getFeedback(),
                submitter == null ? null : submitter.getId(),
                submitter == null ? null : submitter.getFullName(),
                result.getSubmittedAt());
    }

    private InterviewResponse build(
            Interview interview, List<InterviewQuestionResponse> questions, boolean resultSubmitted) {
        User interviewer = interview.getInterviewer();
        return new InterviewResponse(
                interview.getId(),
                interview.getCandidate().getId(),
                interview.getCandidate().fullName(),
                interview.getCandidate().getEmail(),
                interviewer == null ? null : interviewer.getId(),
                interviewer == null ? null : interviewer.getFullName(),
                interview.getTitle(),
                interview.getRoleTitle(),
                interview.getExperienceLevel(),
                interview.getRoundNumber(),
                interview.getScheduledAt(),
                interview.getDurationMinutes(),
                interview.getStatus(),
                interview.focusSkillList(),
                questions,
                resultSubmitted,
                interview.getCreatedAt(),
                interview.getUpdatedAt());
    }
}
