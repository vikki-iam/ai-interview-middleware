package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.Difficulty;
import ai.interview.middleware.domain.enums.QuestionSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "InterviewQuestionResponse", description = "One question on an interview")
public record InterviewQuestionResponse(
        UUID id,
        int sequenceNo,
        String questionText,
        String category,
        Difficulty difficulty,
        @Schema(description = "Interviewer-only guidance; withheld from CANDIDATE callers")
        String expectedAnswer,
        QuestionSource source,
        @Schema(description = "AI service question-set id this came from, if any") UUID externalSetId) {}
