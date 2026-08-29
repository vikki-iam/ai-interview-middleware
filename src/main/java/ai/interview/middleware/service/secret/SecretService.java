package ai.interview.middleware.service.secret;

/**
 * Source of all secret material.
 *
 * <p>This interface is the reason the application needs no code change to move from a laptop to EKS:
 * one implementation reads environment variables, the other reads AWS Secrets Manager through the
 * default credential provider chain (which resolves the IRSA-projected token in-cluster). The
 * implementation is selected by {@code app.secrets.provider} at startup, and nothing downstream knows
 * which one it got.
 *
 * <p>Implementations must never log secret values and must fail loudly at startup rather than return
 * a partially-populated result.
 */
public interface SecretService {

    /** Identifier for logs, {@code /actuator/info} and troubleshooting: {@code env} or {@code aws}. */
    String providerId();

    /**
     * @throws ai.interview.middleware.exception.SecretResolutionException if the credentials cannot be
     *     resolved or are incomplete
     */
    DatabaseCredentials databaseCredentials();

    /**
     * @throws ai.interview.middleware.exception.SecretResolutionException if the credentials cannot be
     *     resolved or are incomplete
     */
    SecurityCredentials securityCredentials();
}
