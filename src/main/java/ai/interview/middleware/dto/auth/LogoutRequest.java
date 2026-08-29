package ai.interview.middleware.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Optional body for logout.
 *
 * <p>The access token is always revoked from the {@code Authorization} header. Supplying the refresh
 * token too revokes the whole session; omitting it leaves the refresh token usable until it expires,
 * which is almost never what a client wants.
 */
@Schema(name = "LogoutRequest", description = "Optionally revokes the refresh token as well")
public record LogoutRequest(
        @Schema(description = "Refresh token to revoke alongside the access token")
        String refreshToken) {}
