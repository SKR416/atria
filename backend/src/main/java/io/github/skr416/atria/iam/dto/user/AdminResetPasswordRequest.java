package io.github.skr416.atria.iam.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank
        @Size(min = 8, max = 100)
        String temporaryPassword,

        Boolean mustChangePassword
) {
    public AdminResetPasswordRequest {
        if (mustChangePassword == null) {
            mustChangePassword = Boolean.TRUE;
        }
    }
}
