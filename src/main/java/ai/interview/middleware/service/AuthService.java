package ai.interview.middleware.service;

import ai.interview.middleware.domain.entity.RevokedToken;
import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.TokenType;
import ai.interview.middleware.dto.auth.LoginRequest;
import ai.interview.middleware.dto.auth.TokenResponse;
import ai.interview.middleware.dto.auth.UserResponse;
import ai.interview.middleware.exception.InvalidTokenException;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.mapper.UserMapper;
import ai.interview.middleware.repository.RevokedTokenRepository;
import ai.interview.middleware.repository.UserRepository;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.security.IssuedToken;
import ai.interview.middleware.security.JwtService;
import ai.interview.middleware.security.TokenClaims;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Login, refresh and logout.
 *
 * <p>Authentication is stateless: the only server-side state is the {@code revoked_tokens} table,
 * which exists so logout takes effect immediately rather than at token expiry. Refresh rotates the
 * token and revokes the presented one, so a stolen refresh token is usable at most once and its reuse
 * is detectable.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final Counter loginSuccesses;
    private final Counter loginFailures;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository,
            JwtService jwtService,
            UserMapper userMapper,
            MeterRegistry meterRegistry) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.loginSuccesses =
                Counter.builder("auth.login")
                        .tag("outcome", "success")
                        .description("Successful logins")
                        .register(meterRegistry);
        this.loginFailures =
                Counter.builder("auth.login")
                        .tag("outcome", "failure")
                        .description("Rejected logins")
                        .register(meterRegistry);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException e) {
            loginFailures.increment();
            log.warn("Login rejected for disabled account {}", email);
            throw new InvalidTokenException("This account has been disabled");
        } catch (AuthenticationException e) {
            // Covers BadCredentialsException and every other authentication failure. A single
            // branch is deliberate: distinguishing them in the response would leak whether the
            // account exists.
            loginFailures.increment();
            log.warn("Login failed for {}", email);
            throw new InvalidTokenException("Invalid email or password");
        }

        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        // Only reachable if the account is deleted between authentication and this read.
                        .orElseThrow(() -> new ResourceNotFoundException("User", email));

        Instant now = Instant.now();
        userRepository.touchLastLogin(user.getId(), now);
        user.setLastLoginAt(now);

        loginSuccesses.increment();
        log.info("Login succeeded for {} (role={})", email, user.getRole());
        return issueTokenPair(user);
    }

    /**
     * Exchanges a refresh token for a new pair.
     *
     * <p>The presented refresh token is revoked in the same transaction as the new one is issued, so
     * replaying it fails.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        TokenClaims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
        if (revokedTokenRepository.existsById(claims.jti())) {
            // Either a logout already happened or the token is being replayed. Both are refusals.
            log.warn("Refresh attempted with a revoked token for user {}", claims.userId());
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        User user =
                userRepository
                        .findByIdAndEnabledTrue(claims.userId())
                        .orElseThrow(
                                () -> new InvalidTokenException("Account no longer exists or has been disabled"));

        revoke(claims.jti(), claims.userId(), TokenType.REFRESH, claims.expiresAt());
        log.info("Refreshed token pair for {}", user.getEmail());
        return issueTokenPair(user);
    }

    /**
     * Revokes the presented access token and, when supplied, the refresh token.
     *
     * <p>Idempotent: logging out twice is not an error, which matters because a client that has already
     * discarded its tokens should still be able to complete the call.
     */
    @Transactional
    public void logout(AuthenticatedUser currentUser, String accessTokenJti, String refreshToken) {
        if (StringUtils.hasText(accessTokenJti)) {
            // The access token's own expiry bounds how long the row is useful; the cleanup job
            // removes it afterwards.
            revoke(accessTokenJti, currentUser.getId(), TokenType.ACCESS, Instant.now().plus(jwtService.accessTokenTtl()));
        }
        if (StringUtils.hasText(refreshToken)) {
            try {
                TokenClaims claims = jwtService.parse(refreshToken, TokenType.REFRESH);
                revoke(claims.jti(), claims.userId(), TokenType.REFRESH, claims.expiresAt());
            } catch (InvalidTokenException e) {
                // An expired or malformed refresh token needs no revocation; it is already unusable.
                log.debug("Ignoring unusable refresh token during logout: {}", e.getMessage());
            }
        }
        log.info("Logout completed for {}", currentUser.getEmail());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthenticatedUser currentUser) {
        return userRepository
                .findById(currentUser.getId())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getId()));
    }

    private TokenResponse issueTokenPair(User user) {
        IssuedToken accessToken = jwtService.issueAccessToken(user);
        IssuedToken refreshToken = jwtService.issueRefreshToken(user);
        return TokenResponse.of(
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresAt(),
                jwtService.accessTokenTtl().toSeconds(),
                userMapper.toResponse(user));
    }

    private void revoke(String jti, java.util.UUID userId, TokenType type, Instant expiresAt) {
        if (revokedTokenRepository.existsById(jti)) {
            return;
        }
        revokedTokenRepository.save(new RevokedToken(jti, userId, type, expiresAt));
    }
}
