package ai.interview.middleware.mapper;

import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.dto.auth.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Entity-to-DTO translation for users.
 *
 * <p>Hand-written rather than generated: the whole point of this mapper is that it cannot
 * accidentally start copying {@code passwordHash} when a field is added to the entity.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.getLastLoginAt());
    }
}
