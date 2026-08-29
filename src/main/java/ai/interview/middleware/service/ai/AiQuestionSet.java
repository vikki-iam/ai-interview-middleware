package ai.interview.middleware.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * Wire contract returned by the AI service.
 *
 * <p>Unknown fields are ignored so the AI service can add response fields without a coordinated
 * middleware release.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiQuestionSet(
        UUID setId,
        String requestId,
        String provider,
        String model,
        int latencyMs,
        List<AiQuestion> questions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiQuestion(
            int sequenceNo,
            String questionText,
            String category,
            String difficulty,
            String expectedAnswer,
            String evaluationHint) {}
}
