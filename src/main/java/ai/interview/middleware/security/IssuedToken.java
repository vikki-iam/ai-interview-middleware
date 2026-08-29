package ai.interview.middleware.security;

import java.time.Instant;

/** A freshly minted JWT plus the metadata the caller needs to revoke or describe it. */
public record IssuedToken(String token, String jti, Instant expiresAt) {

    @Override
    public String toString() {
        // The token is bearer credentials; keep it out of logs even by accident.
        return "IssuedToken[jti=%s, expiresAt=%s, token=***]".formatted(jti, expiresAt);
    }
}
