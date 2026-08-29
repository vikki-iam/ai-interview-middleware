package ai.interview.middleware.service.ai;

/**
 * Abstraction over the AI question-generation service.
 *
 * <p>An interface rather than a direct {@code RestClient} call so tests can substitute a stub and so
 * an alternative provider (a queue, a different service) can be introduced without touching
 * {@code InterviewService}.
 */
public interface AiQuestionClient {

    /**
     * Generates a question set.
     *
     * @throws ai.interview.middleware.exception.AiServiceException if the service is unreachable,
     *     rejects the request, or returns an unusable payload after all retry attempts
     */
    AiQuestionSet generateQuestions(AiQuestionRequest request);

    /** Cheap liveness probe used by the actuator health contributor. */
    boolean isReachable();
}
