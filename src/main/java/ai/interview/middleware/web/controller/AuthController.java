package ai.interview.middleware.web.controller;

import ai.interview.middleware.dto.auth.LoginRequest;
import ai.interview.middleware.dto.auth.LogoutRequest;
import ai.interview.middleware.dto.auth.RefreshTokenRequest;
import ai.interview.middleware.dto.auth.TokenResponse;
import ai.interview.middleware.dto.auth.UserResponse;
import ai.interview.middleware.security.AuthenticatedUser;
import ai.interview.middleware.security.CurrentUserProvider;
import ai.interview.middleware.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>{@code /login} and {@code /refresh} are the only unauthenticated endpoints in the API, so they
 * override the global bearer requirement with {@link SecurityRequirements} to keep Swagger UI honest.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, token refresh and logout")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Exchange credentials for a token pair",
            description = "Returns a short-lived access token and a longer-lived refresh token.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Malformed request")
    @ApiResponse(responseCode = "401", description = "Invalid credentials or disabled account")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Rotate a refresh token",
            description =
                    "Returns a new token pair and revokes the presented refresh token, so each refresh "
                            + "token is usable exactly once.")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(responseCode = "401", description = "Refresh token is expired, revoked or invalid")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Revoke the current session",
            description =
                    "Revokes the access token from the Authorization header. Supply refreshToken to "
                            + "revoke the whole session. Idempotent.")
    @ApiResponse(responseCode = "204", description = "Session revoked")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.require();
        authService.logout(
                currentUser, currentUser.getTokenId(), request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Describe the authenticated user")
    @ApiResponse(responseCode = "200", description = "The authenticated user")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authService.currentUser(currentUserProvider.require()));
    }
}
