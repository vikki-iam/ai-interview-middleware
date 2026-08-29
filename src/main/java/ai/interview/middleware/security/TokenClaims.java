package ai.interview.middleware.security;

import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.domain.enums.TokenType;
import java.time.Instant;
import java.util.UUID;

/** The verified contents of a JWT, after signature, issuer, expiry and type checks have passed. */
public record TokenClaims(
        UUID userId, String email, String fullName, Role role, TokenType tokenType, String jti, Instant expiresAt) {}
