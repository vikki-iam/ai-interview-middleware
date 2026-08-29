package ai.interview.middleware.web.health;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.service.ai.AiQuestionClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports AI service reachability as the {@code aiService} component of {@code /actuator/health}.
 *
 * <p>Deliberately excluded from the {@code readiness} group (see {@code application.yml}): if an AI
 * outage marked every middleware pod unready, Kubernetes would pull them all from the Service and
 * take down login and candidate management too. Question generation degrades; the rest keeps serving.
 */
@Component("aiService")
public class AiServiceHealthIndicator implements HealthIndicator {

    private final AiQuestionClient aiQuestionClient;
    private final String baseUrl;

    public AiServiceHealthIndicator(AiQuestionClient aiQuestionClient, AppProperties properties) {
        this.aiQuestionClient = aiQuestionClient;
        this.baseUrl = properties.ai().baseUrl();
    }

    @Override
    public Health health() {
        boolean reachable = aiQuestionClient.isReachable();
        Health.Builder builder = reachable ? Health.up() : Health.down();
        return builder
                .withDetail("target", baseUrl)
                .withDetail("impact", reachable ? "none" : "question generation unavailable")
                .build();
    }
}
