package ai.interview.middleware.security;

import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.Role;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal.
 *
 * <p>Holds only what authorization decisions need. It is built either from a database row (during
 * login) or directly from verified JWT claims (on every subsequent request), which is why
 * {@link #getPassword()} may legitimately be null.
 */
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String fullName;
    private final Role role;
    private final String passwordHash;
    private final boolean enabled;
    private final String tokenId;

    private AuthenticatedUser(
            UUID id,
            String email,
            String fullName,
            Role role,
            String passwordHash,
            boolean enabled,
            String tokenId) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.tokenId = tokenId;
    }

    /** Used during password authentication, where the hash is required. */
    public static AuthenticatedUser fromEntity(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getPasswordHash(),
                user.isEnabled(),
                null);
    }

    /**
     * Used on authenticated requests. No password is involved, and the token's {@code jti} is retained
     * so logout can revoke exactly the token that was presented.
     */
    public static AuthenticatedUser fromClaims(TokenClaims claims) {
        return new AuthenticatedUser(
                claims.userId(),
                claims.email(),
                claims.fullName(),
                claims.role(),
                null,
                true,
                claims.jti());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    /** The {@code jti} of the presented access token, or null when built from a database row. */
    public String getTokenId() {
        return tokenId;
    }

    public boolean hasRole(Role candidate) {
        return role == candidate;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
