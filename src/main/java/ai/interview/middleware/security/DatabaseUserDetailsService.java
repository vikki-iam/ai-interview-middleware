package ai.interview.middleware.security;

import ai.interview.middleware.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads users for password authentication.
 *
 * <p>Only used by the login endpoint. Authenticated requests build their principal from JWT claims,
 * so this is not on the hot path.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmailIgnoreCase(email)
                .map(AuthenticatedUser::fromEntity)
                // The message is generic on purpose: Spring Security maps it to the same
                // BadCredentialsException as a wrong password, so the endpoint cannot be used to
                // enumerate which email addresses have accounts.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
