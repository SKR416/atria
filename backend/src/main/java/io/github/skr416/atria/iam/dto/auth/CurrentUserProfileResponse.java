package io.github.skr416.atria.iam.dto.auth;

import io.github.skr416.atria.iam.model.DataScopeType;
import io.github.skr416.atria.iam.model.Permission;
import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@Builder
@Schema(description = "Current authenticated user profile and security context")
public record CurrentUserProfileResponse(
        @Schema(description = "Primary user ID", example = "1")
        Long id,

        @Schema(description = "Account username", example = "admin")
        String username,

        @Schema(description = "User display name", example = "System Administrator")
        String displayName,

        @Schema(description = "Email address", example = "admin@atria.local")
        String email,

        @Schema(description = "Assigned department ID", example = "1")
        Long departmentId,

        @Schema(description = "Department materialized tree path", example = "/1/")
        String departmentTreePath,

        @Schema(description = "Department timezone", example = "Asia/Taipei")
        String departmentTimezone,

        @Schema(description = "Department calendar code", example = "TW")
        String departmentCalendarCode,

        @Schema(description = "User preferred locale", example = "zh-TW")
        String locale,

        @Schema(description = "Whether password change is required", example = "false")
        boolean mustChangePassword,

        @Schema(description = "Effective data scope across assigned roles", example = "ALL")
        DataScopeType dataScope,

        @Schema(description = "Assigned role codes", example = "[\"SUPER_ADMIN\"]")
        Set<String> roles,

        @Schema(description = "Granted permission codes", example = "[\"iam:user:read\", \"iam:user:write\"]")
        Set<String> permissions
) {

    public static CurrentUserProfileResponse from(User user, DataScopeType dataScope, String departmentTreePath) {
        return from(user, dataScope, departmentTreePath, "Asia/Taipei", "TW");
    }

    public static CurrentUserProfileResponse from(
            User user,
            DataScopeType dataScope,
            String departmentTreePath,
            String departmentTimezone,
            String departmentCalendarCode
    ) {
        if (user == null) {
            return null;
        }
        Set<String> roleCodes = new TreeSet<>();
        Set<String> permissionCodes = new TreeSet<>();

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getCode() != null) {
                    roleCodes.add(role.getCode());
                }
                if (role.getPermissions() != null) {
                    for (Permission perm : role.getPermissions()) {
                        if (perm.getCode() != null) {
                            permissionCodes.add(perm.getCode());
                        }
                    }
                }
            }
        }

        return CurrentUserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .departmentId(user.getDepartmentId())
                .departmentTreePath(departmentTreePath)
                .departmentTimezone(departmentTimezone != null ? departmentTimezone : "Asia/Taipei")
                .departmentCalendarCode(departmentCalendarCode != null ? departmentCalendarCode : "TW")
                .locale(user.getLocale())
                .mustChangePassword(user.isMustChangePassword())
                .dataScope(dataScope != null ? dataScope : DataScopeType.SELF_ONLY)
                .roles(Collections.unmodifiableSet(roleCodes))
                .permissions(Collections.unmodifiableSet(permissionCodes))
                .build();
    }

    public static CurrentUserProfileResponse fromPrincipal(AtriaUserPrincipal principal, String email) {
        if (principal == null) {
            return null;
        }
        return CurrentUserProfileResponse.builder()
                .id(principal.getUserId())
                .username(principal.getUsername())
                .displayName(principal.getDisplayName())
                .email(email)
                .departmentId(principal.getDepartmentId())
                .departmentTreePath(principal.getDepartmentTreePath())
                .departmentTimezone(principal.getDepartmentTimezone() != null ? principal.getDepartmentTimezone() : "Asia/Taipei")
                .departmentCalendarCode(principal.getDepartmentCalendarCode() != null ? principal.getDepartmentCalendarCode() : "TW")
                .locale(principal.getLocale())
                .mustChangePassword(principal.isMustChangePassword())
                .dataScope(principal.getDataScope() != null ? principal.getDataScope() : DataScopeType.SELF_ONLY)
                .roles(principal.getRoleCodes() != null ? principal.getRoleCodes() : Collections.emptySet())
                .permissions(principal.getPermissionCodes() != null ? principal.getPermissionCodes() : Collections.emptySet())
                .build();
    }
}
