package ai.interview.middleware.security;

import ai.interview.middleware.exception.InvalidTokenException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated principal from the security context.
 *
 * <p>A bean rather than a static helper so services that need the caller can declare the dependency
 * and be tested with a stub, instead of every test having to prime {@link SecurityContextHolder}.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * @throws InvalidTokenException if there is no authenticated principal, which can only happen if
     *     an endpoint was accidentally left outside the authorization rules
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new InvalidTokenException("No authenticated user in context"));
    }
}
