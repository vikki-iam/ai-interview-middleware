package ai.interview.middleware.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration tree.
 *
 * <p>Every value is sourced from an environment variable in {@code application.yml}. Binding fails
 * fast at startup on a missing or malformed value, which is what turns a broken ConfigMap into a
 * pod that never becomes ready rather than a service that 500s under load.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @Valid @NotNull @NestedConfigurationProperty Api api,
        @Valid @NotNull @NestedConfigurationProperty Secrets secrets,
        @Valid @NotNull @NestedConfigurationProperty Datasource datasource,
        @Valid @NotNull @NestedConfigurationProperty Jwt jwt,
        @Valid @NotNull @NestedConfigurationProperty Cors cors,
        @Valid @NotNull @NestedConfigurationProperty Storage storage,
        @Valid @NotNull @NestedConfigurationProperty Ai ai) {

    /** Which backend supplies secrets. Switching this requires no code change. */
    public enum SecretProvider {
        ENV,
        AWS
    }

    /** Which backend stores resume bytes. */
    public enum StorageBackend {
        LOCAL,
        S3
    }

    public record Api(@NotBlank String basePath) {}

    public record Secrets(
            @NotNull SecretProvider provider,
            @Valid @NotNull @NestedConfigurationProperty EnvSecrets env,
            @Valid @NotNull @NestedConfigurationProperty AwsSecrets aws) {}

    /**
     * Local/dev secret material. Present even when {@code provider=AWS} so the same configuration
     * file is valid in every environment; the AWS provider simply ignores it.
     */
    public record EnvSecrets(
            @NotBlank String dbHost,
            @Min(1) @Max(65535) int dbPort,
            @NotBlank String dbName,
            @NotBlank String dbUsername,
            @NotBlank String dbPassword,
            @NotBlank String jwtSigningKey,
            @NotBlank String aiServiceApiKey) {}

    public record AwsSecrets(
            String region,
            String databaseSecretId,
            String applicationSecretId,
            @NotNull Duration cacheTtl) {}

    public record Datasource(
            @Min(1) @Max(200) int maximumPoolSize,
            @Min(0) int minimumIdle,
            @NotNull Duration connectionTimeout,
            @NotNull Duration idleTimeout,
            @NotNull Duration maxLifetime,
            @NotNull Duration validationTimeout,
            @NotBlank String sslMode) {}

    public record Jwt(
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration clockSkew) {}

    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            @NotNull Duration maxAge) {}

    public record Storage(
            @NotNull StorageBackend type,
            @Min(1024) long maxFileSizeBytes,
            @NotEmpty List<String> allowedContentTypes,
            @Valid @NotNull @NestedConfigurationProperty LocalStorage local,
            @Valid @NotNull @NestedConfigurationProperty S3Storage s3) {}

    public record LocalStorage(@NotBlank String root) {}

    public record S3Storage(String bucket, String prefix, String region) {}

    public record Ai(
            @NotBlank String baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout,
            @Min(1) @Max(5) int maxAttempts,
            @NotBlank String healthPath) {}
}
