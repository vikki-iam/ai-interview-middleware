package ai.interview.middleware.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS SDK clients, created only when the corresponding feature is switched on.
 *
 * <p>Every client uses {@link DefaultCredentialsProvider}. That single decision is what makes IRSA
 * work: in-cluster the chain picks up the projected web identity token and assumes the role annotated
 * on the ServiceAccount; on a developer machine the same chain finds the SSO profile. There is no
 * branch on environment, and no code path that reads an access key.
 *
 * <p>Region resolution is left to the SDK when {@code AWS_REGION} is unset, so an explicit region is
 * an override rather than a requirement.
 */
@Configuration(proxyBeanMethods = false)
public class AwsClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsClientConfig.class);

    /** Overrides the Secrets Manager endpoint; used by LocalStack in integration tests. */
    private static final String LOCALSTACK_ENDPOINT_ENV = "AWS_ENDPOINT_URL";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.secrets.provider", havingValue = "aws")
    @ConditionalOnMissingBean
    public SecretsManagerClient secretsManagerClient(AppProperties properties) {
        var builder =
                SecretsManagerClient.builder().credentialsProvider(DefaultCredentialsProvider.create());
        applyRegion(properties.secrets().aws().region(), builder::region);
        applyEndpointOverride(builder::endpointOverride);
        log.info("SecretsManagerClient initialised with the default credential provider chain");
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    @ConditionalOnMissingBean
    public S3Client s3Client(AppProperties properties) {
        var builder = S3Client.builder().credentialsProvider(DefaultCredentialsProvider.create());
        applyRegion(properties.storage().s3().region(), builder::region);
        applyEndpointOverride(builder::endpointOverride);
        log.info("S3Client initialised with the default credential provider chain");
        return builder.build();
    }

    private void applyRegion(String configuredRegion, java.util.function.Consumer<Region> setter) {
        if (StringUtils.hasText(configuredRegion)) {
            setter.accept(Region.of(configuredRegion));
        }
    }

    private void applyEndpointOverride(java.util.function.Consumer<URI> setter) {
        String endpoint = System.getenv(LOCALSTACK_ENDPOINT_ENV);
        if (StringUtils.hasText(endpoint)) {
            log.info("Overriding AWS endpoint with {}", endpoint);
            setter.accept(URI.create(endpoint));
        }
    }
}
