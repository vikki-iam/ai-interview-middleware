package ai.interview.middleware.exception;

/**
 * Thrown when secret material cannot be resolved at startup.
 *
 * <p>Not an {@link ApiException}: this fails during context refresh, so the pod never becomes ready
 * and a misconfigured IRSA role surfaces as a CrashLoopBackOff rather than as runtime 500s.
 */
public class SecretResolutionException extends RuntimeException {

    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
