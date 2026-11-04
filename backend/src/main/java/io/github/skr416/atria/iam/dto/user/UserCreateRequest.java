package io.github.skr416.atria.iam.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

public record UserCreateRequest(
        @NotBlank
        @Size(max = 64)
        String username,

        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String initialPassword,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotNull
        Long departmentId,

        LocalDate hireDate,

        String locale,

        Set<Long> roleIds,

        Boolean mustChangePassword
) {
    public UserCreateRequest {
        if (email != null && email.isBlank()) {
            email = null;
        }
        if (locale == null || locale.isBlank()) {
            locale = "zh-TW";
        }
        if (roleIds == null) {
            roleIds = Collections.emptySet();
        }
        if (mustChangePassword == null) {
            mustChangePassword = Boolean.TRUE;
        }
    }
}
