package ai.interview.middleware.service.secret;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.exception.SecretResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Reads secrets from configuration, which in every deployment means environment variables (see
 * {@code application.yml}).
 *
 * <p>Selected when {@code app.secrets.provider=env}. Intended for local development, Docker Compose
 * and CI. Using it in production means credentials are visible in the pod spec, so
 * {@code values-prod.yaml} sets the provider to {@code aws}.
 */
@Service
@ConditionalOnProperty(name = "app.secrets.provider", havingValue = "env", matchIfMissing = true)
public class EnvironmentSecretService implements SecretService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentSecretService.class);
    private static final int MIN_HS256_KEY_BYTES = 32;

    private final AppProperties.EnvSecrets secrets;

    public EnvironmentSecretService(AppProperties properties) {
        this.secrets = properties.secrets().env();
        log.info(
                "Secret provider 'env' active; database target {}:{}/{}",
                secrets.dbHost(),
                secrets.dbPort(),
                secrets.dbName());
    }

    @Override
    public String providerId() {
        return "env";
    }

    @Override
    public DatabaseCredentials databaseCredentials() {
        return new DatabaseCredentials(
                secrets.dbHost(),
                secrets.dbPort(),
                secrets.dbName(),
                secrets.dbUsername(),
                secrets.dbPassword());
    }

    @Override
    public SecurityCredentials securityCredentials() {
        String signingKey = secrets.jwtSigningKey();
        // HS256 with a key shorter than its 256-bit output is a real weakness, not a style issue,
        // so this is a startup failure rather than a warning.
        if (signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_HS256_KEY_BYTES) {
            throw new SecretResolutionException(
                    "JWT_SIGNING_KEY must be at least %d bytes for HS256; got %d"
                            .formatted(
                                    MIN_HS256_KEY_BYTES,
                                    signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        }
        return new SecurityCredentials(signingKey, secrets.aiServiceApiKey());
    }
}
