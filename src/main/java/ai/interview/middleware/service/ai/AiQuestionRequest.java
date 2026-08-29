package ai.interview.middleware.service.ai;

import java.util.List;
import java.util.UUID;

/**
 * Wire contract sent to {@code POST /api/v1/questions/generate} on the AI service.
 *
 * <p>Kept separate from the inbound {@code GenerateQuestionsRequest} DTO: the two evolve
 * independently, and coupling them would make an AI-service contract change a public API change.
 */
public record AiQuestionRequest(
        UUID interviewId,
        String roleTitle,
        String experienceLevel,
        List<String> skills,
        int questionCount) {}
