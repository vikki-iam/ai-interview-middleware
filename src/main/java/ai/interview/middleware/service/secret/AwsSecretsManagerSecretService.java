package ai.interview.middleware.service.secret;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.exception.SecretResolutionException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Reads secrets from AWS Secrets Manager.
 *
 * <p>Selected when {@code app.secrets.provider=aws}. The injected {@link SecretsManagerClient} is
 * built with the default credential provider chain, which is what makes IRSA work: in-cluster the
 * chain finds {@code AWS_WEB_IDENTITY_TOKEN_FILE} and {@code AWS_ROLE_ARN} projected by the
 * ServiceAccount annotation and calls {@code sts:AssumeRoleWithWebIdentity}. There is no access key
 * in this class, in configuration, or in the image.
 *
 * <p>Values are cached for {@code app.secrets.aws.cache-ttl} so a rotation is picked up without a
 * restart, while a hot path never turns into a Secrets Manager API call.
 */
@Service
@ConditionalOnProperty(name = "app.secrets.provider", havingValue = "aws")
public class AwsSecretsManagerSecretService implements SecretService {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerSecretService.class);
    private static final int MIN_HS256_KEY_BYTES = 32;

    private final SecretsManagerClient client;
    private final ObjectMapper objectMapper;
    private final String databaseSecretId;
    private final String applicationSecretId;
    private final Duration cacheTtl;

    private final AtomicReference<Cached<DatabaseCredentials>> databaseCache = new AtomicReference<>();
    private final AtomicReference<Cached<SecurityCredentials>> securityCache = new AtomicReference<>();

    public AwsSecretsManagerSecretService(
            SecretsManagerClient client, ObjectMapper objectMapper, AppProperties properties) {
        AppProperties.AwsSecrets aws = properties.secrets().aws();
        if (!StringUtils.hasText(aws.databaseSecretId())) {
            throw new SecretResolutionException(
                    "app.secrets.aws.database-secret-id is required when app.secrets.provider=aws "
                            + "(set APP_SECRETS_AWS_DATABASE_SECRET_ID)");
        }
        if (!StringUtils.hasText(aws.applicationSecretId())) {
            throw new SecretResolutionException(
                    "app.secrets.aws.application-secret-id is required when app.secrets.provider=aws "
                            + "(set APP_SECRETS_AWS_APPLICATION_SECRET_ID)");
        }
        this.client = client;
        this.objectMapper = objectMapper;
        this.databaseSecretId = aws.databaseSecretId();
        this.applicationSecretId = aws.applicationSecretId();
        this.cacheTtl = aws.cacheTtl();
        log.info(
                "Secret provider 'aws' active; databaseSecretId={} applicationSecretId={} cacheTtl={}",
                databaseSecretId,
                applicationSecretId,
                cacheTtl);
    }

    @Override
    public String providerId() {
        return "aws";
    }

    @Override
    public DatabaseCredentials databaseCredentials() {
        return cached(databaseCache, this::loadDatabaseCredentials);
    }

    @Override
    public SecurityCredentials securityCredentials() {
        return cached(securityCache, this::loadSecurityCredentials);
    }

    private DatabaseCredentials loadDatabaseCredentials() {
        DatabaseSecretPayload payload =
                parse(fetch(databaseSecretId), DatabaseSecretPayload.class, databaseSecretId);
        requireField(payload.host(), "host", databaseSecretId);
        requireField(payload.username(), "username", databaseSecretId);
        requireField(payload.password(), "password", databaseSecretId);
        requireField(payload.databaseName(), "dbname", databaseSecretId);
        if (payload.port() == null || payload.port() < 1 || payload.port() > 65535) {
            throw new SecretResolutionException(
                    "Secret '%s' has an invalid 'port' value".formatted(databaseSecretId));
        }
        DatabaseCredentials credentials =
                new DatabaseCredentials(
                        payload.host(),
                        payload.port(),
                        payload.databaseName(),
                        payload.username(),
                        payload.password());
        log.info("Resolved database credentials from Secrets Manager: {}", credentials.describe());
        return credentials;
    }

    private SecurityCredentials loadSecurityCredentials() {
        ApplicationSecretPayload payload =
                parse(fetch(applicationSecretId), ApplicationSecretPayload.class, applicationSecretId);
        requireField(payload.jwtSigningKey(), "jwtSigningKey", applicationSecretId);
        requireField(payload.aiServiceApiKey(), "aiServiceApiKey", applicationSecretId);
        int keyBytes = payload.jwtSigningKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (keyBytes < MIN_HS256_KEY_BYTES) {
            throw new SecretResolutionException(
                    "jwtSigningKey in secret '%s' must be at least %d bytes for HS256; got %d"
                            .formatted(applicationSecretId, MIN_HS256_KEY_BYTES, keyBytes));
        }
        log.info("Resolved application secrets from Secrets Manager id={}", applicationSecretId);
        return new SecurityCredentials(payload.jwtSigningKey(), payload.aiServiceApiKey());
    }

    private String fetch(String secretId) {
        try {
            String value =
                    client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build())
                            .secretString();
            if (!StringUtils.hasText(value)) {
                throw new SecretResolutionException(
                        "Secret '%s' has no SecretString (binary secrets are not supported)"
                                .formatted(secretId));
            }
            return value;
        } catch (ResourceNotFoundException e) {
            throw new SecretResolutionException(
                    "Secret '%s' does not exist in this account/region".formatted(secretId), e);
        } catch (SecretsManagerException e) {
            // An AccessDeniedException here almost always means the IRSA trust policy or the role's
            // secretsmanager:GetSecretValue permission is wrong. Say so, because the raw SDK message
            // sends people looking for missing access keys instead.
            throw new SecretResolutionException(
                    ("Failed to read secret '%s' from Secrets Manager (%s). Verify the IRSA role "
                                    + "annotation on the ServiceAccount and that the role allows "
                                    + "secretsmanager:GetSecretValue on this secret.")
                            .formatted(secretId, e.awsErrorDetails().errorCode()),
                    e);
        }
    }

    private <T> T parse(String json, Class<T> type, String secretId) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            // The message deliberately omits the payload so a malformed secret is not logged verbatim.
            throw new SecretResolutionException(
                    "Secret '%s' is not valid JSON for %s".formatted(secretId, type.getSimpleName()), e);
        }
    }

    private void requireField(String value, String field, String secretId) {
        if (!StringUtils.hasText(value)) {
            throw new SecretResolutionException(
                    "Secret '%s' is missing required field '%s'".formatted(secretId, field));
        }
    }

    private <T> T cached(AtomicReference<Cached<T>> reference, Supplier<T> loader) {
        Cached<T> current = reference.get();
        if (current != null && current.isFresh()) {
            return current.value();
        }
        T loaded = loader.get();
        reference.set(new Cached<>(loaded, Instant.now().plus(cacheTtl)));
        return loaded;
    }

    private record Cached<T>(T value, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /**
     * Matches the JSON that RDS-managed rotation writes, so a secret created by
     * {@code aws rds create-db-instance --manage-master-user-password} works unmodified.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DatabaseSecretPayload(
            String host,
            Integer port,
            String username,
            String password,
            @JsonProperty("dbname") String databaseName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApplicationSecretPayload(String jwtSigningKey, String aiServiceApiKey) {}
}
