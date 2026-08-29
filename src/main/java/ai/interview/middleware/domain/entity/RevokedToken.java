package ai.interview.middleware.domain.entity;

import ai.interview.middleware.domain.enums.TokenType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A JWT that has been explicitly invalidated by logout or refresh rotation.
 *
 * <p>Persisted rather than held in memory so revocation is honoured by every replica and survives a
 * pod restart. Expired rows are pruned by {@code TokenCleanupService}.
 *
 * <p>Deliberately not a {@link BaseEntity}: the JWT id is the natural key and the row is immutable,
 * so it needs neither a surrogate id nor optimistic locking.
 */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected RevokedToken() {
        // Required by JPA.
    }

    public RevokedToken(String jti, UUID userId, TokenType tokenType, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    public String getJti() {
        return jti;
    }

    public UUID getUserId() {
        return userId;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
