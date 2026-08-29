package ai.interview.middleware.config;

import ai.interview.middleware.service.secret.SecretService;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client used to reach the AI service.
 *
 * <p>Timeouts are mandatory, not optional tuning: without them a stalled AI service would hold
 * middleware request threads until the pool is exhausted, turning a degraded dependency into a total
 * outage. The internal API key is attached as a default header so no call site can forget it.
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    @Bean
    public RestClient aiServiceRestClient(AppProperties properties, SecretService secretService) {
        AppProperties.Ai ai = properties.ai();

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(ai.connectTimeout())
                        // Pinned to HTTP/1.1. The JDK client defaults to HTTP_2, which over
                        // plaintext means it opens with an h2c upgrade handshake
                        // (Connection: Upgrade, HTTP2-Settings). Uvicorn serves HTTP/1.1 only and
                        // rejects that handshake outright, so every request fails before the body
                        // is ever parsed. There is no benefit to negotiating here: this is a
                        // single-hop, in-cluster call over a ClusterIP Service.
                        .version(HttpClient.Version.HTTP_1_1)
                        // The AI service is reached by ClusterIP; following a redirect off-cluster
                        // would be a misconfiguration, so redirects are not followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(ai.readTimeout());

        log.info(
                "AI service client -> {} (connectTimeout={} readTimeout={} maxAttempts={})",
                ai.baseUrl(),
                ai.connectTimeout(),
                ai.readTimeout(),
                ai.maxAttempts());

        return RestClient.builder()
                .baseUrl(ai.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(INTERNAL_API_KEY_HEADER, secretService.securityCredentials().aiServiceApiKey())
                .build();
    }
}
