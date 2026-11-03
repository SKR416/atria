package io.github.skr416.atria.iam.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "User login credentials payload")
public record LoginRequest(
        @Schema(description = "Account username", example = "admin")
        @NotBlank(message = "{error.validation}")
        @Size(max = 64)
        String username,

        @Schema(description = "Account password", example = "Admin@12345")
        @NotBlank(message = "{error.validation}")
        @Size(max = 128)
        String password
) {}
