package ai.interview.middleware.service.ai;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.exception.AiServiceException;
import ai.interview.middleware.security.RequestIdFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP implementation of {@link AiQuestionClient}.
 *
 * <p>Retries only transport failures and 5xx responses, with a short fixed backoff. The generation
 * endpoint is idempotent from the middleware's perspective (a retry produces a new question set that
 * is either used or dropped), so a retry cannot corrupt state.
 *
 * <p>Every call is timed and counted so the AI dependency shows up in Grafana as its own signal rather
 * than as unexplained latency in {@code http_server_requests}.
 */
@Service
public class HttpAiQuestionClient implements AiQuestionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiQuestionClient.class);

    private static final String GENERATE_PATH = "/api/v1/questions/generate";
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(250);

    private final RestClient restClient;
    private final String healthPath;
    private final int maxAttempts;
    private final Timer generationTimer;
    private final Counter generationFailures;
    private final Counter generationRetries;

    public HttpAiQuestionClient(
            RestClient aiServiceRestClient, AppProperties properties, MeterRegistry meterRegistry) {
        this.restClient = aiServiceRestClient;
        this.healthPath = properties.ai().healthPath();
        this.maxAttempts = properties.ai().maxAttempts();
        this.generationTimer =
                Timer.builder("ai.question.generation")
                        .description("Time spent generating interview questions via the AI service")
                        .publishPercentileHistogram()
                        .register(meterRegistry);
        this.generationFailures =
                Counter.builder("ai.question.generation.failures")
                        .description("Question generation attempts that failed after all retries")
                        .register(meterRegistry);
        this.generationRetries =
                Counter.builder("ai.question.generation.retries")
                        .description("Retried question generation attempts")
                        .register(meterRegistry);
    }

    @Override
    public AiQuestionSet generateQuestions(AiQuestionRequest request) {
        Timer.Sample sample = Timer.start();
        try {
            AiQuestionSet result = attemptWithRetries(request);
            validate(result, request.questionCount());
            return result;
        } finally {
            sample.stop(generationTimer);
        }
    }

    @Override
    public boolean isReachable() {
        try {
            restClient.get().uri(healthPath).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.debug("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private AiQuestionSet attemptWithRetries(AiQuestionRequest request) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call(request);
            } catch (RetryableAiServiceException e) {
                lastFailure = e;
                if (attempt < maxAttempts) {
                    generationRetries.increment();
                    log.warn(
                            "AI question generation attempt {}/{} failed ({}); retrying",
                            attempt,
                            maxAttempts,
                            e.getMessage());
                    sleepBeforeRetry();
                }
            } catch (AiServiceException e) {
                // Non-retryable (4xx or unusable payload): fail immediately.
                generationFailures.increment();
                throw e;
            }
        }
        generationFailures.increment();
        throw new AiServiceException(
                "AI service did not produce questions after %d attempt(s)".formatted(maxAttempts),
                lastFailure);
    }

    private AiQuestionSet call(AiQuestionRequest request) {
        try {
            return restClient
                    .post()
                    .uri(GENERATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    // Propagates the correlation id so one request id spans both services' logs.
                    .header(RequestIdFilter.HEADER_NAME, currentRequestId())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new RetryableAiServiceException(
                                "AI service returned " + res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new AiServiceException(
                                "AI service rejected the generation request with "
                                        + res.getStatusCode().value());
                    })
                    .body(AiQuestionSet.class);
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, or read timeout: all worth one more attempt.
            throw new RetryableAiServiceException("AI service is unreachable: " + e.getMessage(), e);
        }
    }

    private void validate(AiQuestionSet result, int requestedCount) {
        if (result == null || result.questions() == null || result.questions().isEmpty()) {
            generationFailures.increment();
            throw new AiServiceException("AI service returned an empty question set");
        }
        List<AiQuestionSet.AiQuestion> questions = result.questions();
        if (questions.size() != requestedCount) {
            // Not fatal: fewer usable questions is still useful, and failing the request over an
            // off-by-one from a language model would be worse than logging it.
            log.warn(
                    "AI service returned {} questions but {} were requested (setId={})",
                    questions.size(),
                    requestedCount,
                    result.setId());
        }
        boolean anyBlank =
                questions.stream()
                        .anyMatch(q -> q.questionText() == null || q.questionText().isBlank());
        if (anyBlank) {
            generationFailures.increment();
            throw new AiServiceException("AI service returned a question with no text");
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("Interrupted while retrying the AI service", e);
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "internal" : requestId;
    }
}
