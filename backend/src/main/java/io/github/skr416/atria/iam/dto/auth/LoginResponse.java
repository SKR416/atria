package io.github.skr416.atria.iam.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Authentication response containing access token and user profile")
public record LoginResponse(
        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Token expiration duration in seconds", example = "900")
        long expiresInSeconds,

        @Schema(description = "Indicates whether the user must change their password before continuing", example = "false")
        boolean mustChangePassword,

        @Schema(description = "Current authenticated user profile")
        CurrentUserProfileResponse user
) {}
