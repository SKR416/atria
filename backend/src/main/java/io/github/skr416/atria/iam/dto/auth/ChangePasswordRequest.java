package io.github.skr416.atria.iam.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Password change payload")
public record ChangePasswordRequest(
        @Schema(description = "Current account password", example = "Admin@12345")
        @NotBlank(message = "{error.validation}")
        @Size(max = 128)
        String currentPassword,

        @Schema(description = "New password (at least 8 characters)", example = "NewStrongPass#2026")
        @NotBlank(message = "{error.validation}")
        @Size(min = 8, max = 128)
        String newPassword
) {}
