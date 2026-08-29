package ai.interview.middleware.dto.auth;

import ai.interview.middleware.domain.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** The public view of a user. Never exposes {@code passwordHash}. */
@Schema(name = "UserResponse", description = "A platform user")
public record UserResponse(
        UUID id,
        @Schema(example = "admin@aiinterview.local") String email,
        @Schema(example = "Platform Administrator") String fullName,
        @Schema(example = "ADMIN") Role role,
        boolean enabled,
        Instant lastLoginAt) {}
