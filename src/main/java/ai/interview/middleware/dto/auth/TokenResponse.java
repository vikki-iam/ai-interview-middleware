package ai.interview.middleware.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "TokenResponse", description = "Issued JWT pair and the authenticated principal")
public record TokenResponse(
        @Schema(description = "Short-lived bearer token for API calls") String accessToken,
        @Schema(description = "Long-lived token used only against /auth/refresh") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token expiry", example = "2026-08-04T10:45:30Z") Instant expiresAt,
        @Schema(description = "Access token lifetime in seconds", example = "1800") long expiresInSeconds,
        UserResponse user) {

    public static TokenResponse of(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiry,
            long expiresInSeconds,
            UserResponse user) {
        return new TokenResponse(
                accessToken, refreshToken, "Bearer", accessTokenExpiry, expiresInSeconds, user);
    }
}
