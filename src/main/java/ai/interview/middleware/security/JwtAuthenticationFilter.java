package ai.interview.middleware.security;

import ai.interview.middleware.common.ErrorCode;
import ai.interview.middleware.domain.enums.TokenType;
import ai.interview.middleware.exception.InvalidTokenException;
import ai.interview.middleware.repository.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its {@code Authorization: Bearer} header.
 *
 * <p>The principal is built from the verified claims rather than from a database row, so a normal API
 * call costs no user lookup. The one query it does make is a primary-key check against
 * {@code revoked_tokens}, which is what makes logout take effect immediately across every replica
 * instead of waiting out the token's TTL.
 *
 * <p>A malformed or revoked token is answered here with a JSON 401: this runs long before any
 * {@code @ControllerAdvice}, so the response has to be written directly.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MDC_USER = "userId";

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            RevokedTokenRepository revokedTokenRepository,
            SecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token == null) {
            // No credentials presented. Whether that is acceptable is decided by the authorization
            // rules, not here, so the chain continues as an anonymous request.
            chain.doFilter(request, response);
            return;
        }

        try {
            TokenClaims claims = jwtService.parse(token, TokenType.ACCESS);
            if (revokedTokenRepository.existsById(claims.jti())) {
                throw new InvalidTokenException("Token has been revoked");
            }
            authenticate(request, claims);
            MDC.put(MDC_USER, claims.userId().toString());
            chain.doFilter(request, response);
        } catch (InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            log.debug("Rejecting request to {}: {}", request.getRequestURI(), e.getMessage());
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    ErrorCode.INVALID_TOKEN,
                    e.getMessage());
        } finally {
            MDC.remove(MDC_USER);
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request, TokenClaims claims) {
        AuthenticatedUser principal = AuthenticatedUser.fromClaims(claims);
        var authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Skips the probe and documentation endpoints.
     *
     * <p>Not a security control (they are also {@code permitAll}); it keeps a kubelet probe every few
     * seconds from doing pointless work, and keeps probe traffic out of the auth logs.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/actuator/prometheus")
                || path.equals("/actuator/info");
    }
}
