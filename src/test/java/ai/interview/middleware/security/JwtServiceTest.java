package ai.interview.middleware.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.domain.enums.TokenType;
import ai.interview.middleware.exception.InvalidTokenException;
import ai.interview.middleware.service.secret.SecretService;
import ai.interview.middleware.service.secret.SecurityCredentials;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for token issuing and verification.
 *
 * <p>No Spring context: {@link JwtService} takes only a {@link SecretService} and {@link
 * AppProperties}, so the whole security-critical surface is testable in milliseconds with no database
 * and no container.
 */
class JwtServiceTest {

    private static final String SIGNING_KEY = "unit-test-signing-key-with-more-than-32-bytes-of-entropy";
    private static final String OTHER_SIGNING_KEY = "a-completely-different-key-also-longer-than-32-bytes";

    private JwtService jwtService(String signingKey, Duration accessTtl) {
        SecretService secretService = mock(SecretService.class);
        when(secretService.securityCredentials())
                .thenReturn(new SecurityCredentials(signingKey, "internal-api-key"));
        return new JwtService(secretService, properties(accessTtl));
    }

    private AppProperties properties(Duration accessTtl) {
        return new AppProperties(
                new AppProperties.Api("/api"),
                new AppProperties.Secrets(
                        AppProperties.SecretProvider.ENV,
                        new AppProperties.EnvSecrets(
                                "localhost", 5432, "db", "user", "password", SIGNING_KEY, "internal-api-key"),
                        new AppProperties.AwsSecrets(null, null, null, Duration.ofMinutes(10))),
                new AppProperties.Datasource(
                        10,
                        2,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(25),
                        Duration.ofSeconds(5),
                        "prefer"),
                new AppProperties.Jwt(
                        "ai-interview-platform", accessTtl, Duration.ofHours(8), Duration.ofSeconds(30)),
                new AppProperties.Cors(
                        java.util.List.of("http://localhost:3000"),
                        java.util.List.of("GET"),
                        java.util.List.of("Authorization"),
                        java.util.List.of(),
                        false,
                        Duration.ofMinutes(30)),
                new AppProperties.Storage(
                        AppProperties.StorageBackend.LOCAL,
                        10_485_760L,
                        java.util.List.of("application/pdf"),
                        new AppProperties.LocalStorage("./target/test-resumes"),
                        new AppProperties.S3Storage(null, null, null)),
                new AppProperties.Ai(
                        "http://localhost:8000",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(45),
                        2,
                        "/health/liveness"));
    }

    private User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("priya.sharma@aiinterview.local");
        user.setFullName("Priya Sharma");
        user.setRole(role);
        user.setPasswordHash("$2b$10$irrelevant");
        user.setEnabled(true);
        return user;
    }

    @Test
    @DisplayName("an access token round-trips every claim the filter depends on")
    void accessTokenRoundTrip() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(30));
        User user = user(Role.INTERVIEWER);

        IssuedToken issued = service.issueAccessToken(user);
        TokenClaims claims = service.parse(issued.token(), TokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.email()).isEqualTo(user.getEmail());
        assertThat(claims.fullName()).isEqualTo(user.getFullName());
        assertThat(claims.role()).isEqualTo(Role.INTERVIEWER);
        assertThat(claims.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.jti()).isEqualTo(issued.jti());
        assertThat(claims.expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    @DisplayName("each issued token gets a distinct jti so revocation targets one token")
    void tokensHaveDistinctIdentifiers() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(30));
        User user = user(Role.ADMIN);

        IssuedToken first = service.issueAccessToken(user);
        IssuedToken second = service.issueAccessToken(user);

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    /**
     * The type claim is the control that stops an 8-hour refresh token being replayed as a 30-minute
     * access token, so it is verified in both directions.
     */
    @Test
    @DisplayName("a refresh token is rejected where an access token is required")
    void refreshTokenRejectedAsAccessToken() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(30));
        IssuedToken refresh = service.issueRefreshToken(user(Role.CANDIDATE));

        assertThatThrownBy(() -> service.parse(refresh.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Expected a ACCESS token");
    }

    @Test
    @DisplayName("an access token is rejected where a refresh token is required")
    void accessTokenRejectedAsRefreshToken() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(30));
        IssuedToken access = service.issueAccessToken(user(Role.CANDIDATE));

        assertThatThrownBy(() -> service.parse(access.token(), TokenType.REFRESH))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Expected a REFRESH token");
    }

    /** A token signed with a different key must not verify, or rotation would be meaningless. */
    @Test
    @DisplayName("a token signed with another key does not verify")
    void tokenFromAnotherKeyIsRejected() {
        JwtService issuer = jwtService(OTHER_SIGNING_KEY, Duration.ofMinutes(30));
        JwtService verifier = jwtService(SIGNING_KEY, Duration.ofMinutes(30));
        IssuedToken foreign = issuer.issueAccessToken(user(Role.ADMIN));

        assertThatThrownBy(() -> verifier.parse(foreign.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is not valid");
    }

    /**
     * Issued with a negative TTL so it is already expired on arrival. This also confirms the
     * 30-second clock skew allowance does not swallow a clearly expired token.
     */
    @Test
    @DisplayName("an expired token is rejected with an actionable message")
    void expiredTokenIsRejected() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(-10));
        IssuedToken expired = service.issueAccessToken(user(Role.ADMIN));

        assertThatThrownBy(() -> service.parse(expired.token(), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token has expired");
    }

    @Test
    @DisplayName("a structurally invalid token is rejected without leaking why")
    void malformedTokenIsRejected() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.parse("not-a-jwt", TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is not valid");
    }

    @Test
    @DisplayName("the access token TTL is reported for the login response")
    void accessTokenTtlIsExposed() {
        JwtService service = jwtService(SIGNING_KEY, Duration.ofMinutes(45));

        assertThat(service.accessTokenTtl()).isEqualTo(Duration.ofMinutes(45));
    }
}
