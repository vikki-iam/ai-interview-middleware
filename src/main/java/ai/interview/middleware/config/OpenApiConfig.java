package ai.interview.middleware.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata and the bearer security scheme.
 *
 * <p>No {@code servers} block is declared: leaving it out makes Swagger UI issue requests against
 * whatever host served the page, so the same build works behind the Compose port mapping, a
 * port-forward, and the production Ingress without a per-environment URL.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI aiInterviewOpenApi(@Value("${info.app.version:unknown}") String version) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("AI Interview Platform - Middleware API")
                                .version(version)
                                .description(
                                        """
                                        Middleware API for the AI Interview Platform.

                                        Obtain a token from `POST /api/v1/auth/login`, then click
                                        **Authorize** and paste the `accessToken` value.

                                        Roles: `ADMIN` has full access, `INTERVIEWER` manages
                                        candidates and its own interviews, `CANDIDATE` has read-only
                                        access to its own schedule.
                                        """)
                                .contact(new Contact().name("Platform Engineering").email("platform@aiinterview.local"))
                                .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("Access token issued by /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
