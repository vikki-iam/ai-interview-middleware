package ai.interview.middleware.security;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.domain.enums.TokenType;
import ai.interview.middleware.exception.InvalidTokenException;
import ai.interview.middleware.service.secret.SecretService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JWTs.
 *
 * <p>The signing key comes from {@link SecretService}, so it is an environment variable locally and a
 * Secrets Manager value on EKS. It is read once and held in memory: rotating it requires a rollout,
 * which is the correct trade-off given every issued token would be invalidated anyway.
 *
 * <p>Every token carries a {@code jti}, which is what makes server-side revocation possible without
 * introducing sessions.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final long clockSkewSeconds;

    public JwtService(SecretService secretService, AppProperties properties) {
        AppProperties.Jwt jwt = properties.jwt();
        this.signingKey =
                Keys.hmacShaKeyFor(
                        secretService.securityCredentials().jwtSigningKey().getBytes(StandardCharsets.UTF_8));
        this.issuer = jwt.issuer();
        this.accessTokenTtl = jwt.accessTokenTtl();
        this.refreshTokenTtl = jwt.refreshTokenTtl();
        this.clockSkewSeconds = jwt.clockSkew().toSeconds();
        log.info(
                "JWT service ready: issuer={} accessTtl={} refreshTtl={}",
                issuer,
                accessTokenTtl,
                refreshTokenTtl);
    }

    public IssuedToken issueAccessToken(User user) {
        return issue(user, TokenType.ACCESS, accessTokenTtl);
    }

    public IssuedToken issueRefreshToken(User user) {
        return issue(user, TokenType.REFRESH, refreshTokenTtl);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Verifies signature, issuer and expiry, then checks the token is of the expected type.
     *
     * <p>The type check is what stops a long-lived refresh token from being replayed as an access
     * token against the API.
     *
     * @throws InvalidTokenException on any failure
     */
    public TokenClaims parse(String token, TokenType expectedType) {
        Claims claims = parseClaims(token);
        TokenType actualType = readTokenType(claims);
        if (actualType != expectedType) {
            throw new InvalidTokenException(
                    "Expected a %s token but received a %s token".formatted(expectedType, actualType));
        }
        return toTokenClaims(claims, actualType);
    }

    private IssuedToken issue(User user, TokenType tokenType, Duration ttl) {
        // Truncated to seconds because a JWT's `iat`/`exp` are NumericDate, which has one-second
        // resolution. Without this, the expiry reported to the client in TokenResponse would carry
        // sub-second precision the token itself does not encode, and would disagree with the value a
        // parser reads back from that same token.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();

        String token =
                Jwts.builder()
                        .id(jti)
                        .issuer(issuer)
                        .subject(user.getId().toString())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .claim(CLAIM_EMAIL, user.getEmail())
                        .claim(CLAIM_NAME, user.getFullName())
                        .claim(CLAIM_ROLE, user.getRole().name())
                        .claim(CLAIM_TOKEN_TYPE, tokenType.name())
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();

        return new IssuedToken(token, jti, expiresAt);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // Expiry is the one cause worth distinguishing to the client: it is actionable (refresh)
            // and reveals nothing, unlike a signature failure.
            throw new InvalidTokenException("Token has expired", e);
        } catch (SignatureException e) {
            log.warn("Rejected JWT with an invalid signature");
            throw new InvalidTokenException("Token is not valid", e);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected malformed JWT: {}", e.getMessage());
            throw new InvalidTokenException("Token is not valid", e);
        }
    }

    private TokenType readTokenType(Claims claims) {
        String raw = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (raw == null) {
            throw new InvalidTokenException("Token is missing its type claim");
        }
        try {
            return TokenType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Token has an unrecognised type claim", e);
        }
    }

    private TokenClaims toTokenClaims(Claims claims, TokenType tokenType) {
        try {
            return new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    tokenType,
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Token claims are incomplete or malformed", e);
        }
    }
}
