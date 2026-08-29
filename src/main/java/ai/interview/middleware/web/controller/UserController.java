package ai.interview.middleware.web.controller;

import ai.interview.middleware.domain.enums.Role;
import ai.interview.middleware.dto.auth.UserResponse;
import ai.interview.middleware.mapper.UserMapper;
import ai.interview.middleware.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only user directory.
 *
 * <p>Exists so the interviewer assignment UI has a list to choose from. Deliberately narrow: there is
 * no user creation, update or deletion endpoint, because account provisioning for this platform is a
 * migration and an administrative task rather than a self-service API.
 */
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Read-only directory of platform users")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserController(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Transactional(readOnly = true)
    @Operation(
            summary = "List enabled users by role",
            description = "Defaults to INTERVIEWER, which is what the assignment picker needs.")
    @ApiResponse(responseCode = "200", description = "Matching enabled users, ordered by name")
    public ResponseEntity<List<UserResponse>> listByRole(
            @Parameter(description = "Role to filter by", example = "INTERVIEWER")
            @RequestParam(defaultValue = "INTERVIEWER")
            Role role) {

        List<UserResponse> users =
                userRepository.findAllByRoleAndEnabledTrueOrderByFullNameAsc(role).stream()
                        .map(userMapper::toResponse)
                        .toList();
        return ResponseEntity.ok(users);
    }
}
