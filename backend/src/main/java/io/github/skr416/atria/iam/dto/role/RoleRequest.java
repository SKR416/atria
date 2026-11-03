package io.github.skr416.atria.iam.dto.role;

import io.github.skr416.atria.iam.model.DataScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

/**
 * Request payload for creating or updating a role.
 */
public record RoleRequest(
        @NotBlank(message = "Role code must not be blank")
        @Size(max = 64, message = "Role code must not exceed 64 characters")
        String code,

        @NotEmpty(message = "Role name map must not be empty")
        Map<String, String> name,

        DataScopeType dataScope,

        Set<Long> permissionIds
) {
}
