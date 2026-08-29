package ai.interview.middleware.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Exchanges a refresh token for a new token pair")
public record RefreshTokenRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "refreshToken is required")
        String refreshToken) {}
