package ai.interview.middleware.service.ai;

/**
 * Internal marker for an AI service failure that is worth retrying: a connection problem, a timeout,
 * or a 5xx.
 *
 * <p>Package-private to the AI client because it never escapes it. A 4xx is deliberately excluded:
 * retrying a request the service has already rejected as invalid just doubles the latency before the
 * same failure.
 */
class RetryableAiServiceException extends RuntimeException {

    RetryableAiServiceException(String message) {
        super(message);
    }

    RetryableAiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
