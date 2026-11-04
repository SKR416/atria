package io.github.skr416.atria.iam.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

public record UserUpdateRequest(
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotNull
        Long departmentId,

        LocalDate hireDate,

        String locale,

        Boolean enabled,

        Set<Long> roleIds
) {
    public UserUpdateRequest {
        if (email != null && email.isBlank()) {
            email = null;
        }
        if (roleIds == null) {
            roleIds = Collections.emptySet();
        }
    }
}
