package ai.interview.middleware;

import ai.interview.middleware.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the AI Interview Platform middleware.
 *
 * <p>Nothing environment-specific is configured here. The DataSource is assembled at runtime from
 * {@code SecretService}, which resolves either environment variables (local) or AWS Secrets Manager
 * (EKS) based on {@code app.secrets.provider}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaAuditing
@EnableScheduling
public class MiddlewareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareApplication.class, args);
    }
}
