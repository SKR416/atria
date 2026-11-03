package io.github.skr416.atria.iam.dto.user;

import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.model.User;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        Long departmentId,
        String locale,
        boolean enabled,
        boolean mustChangePassword,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant lastLoginAt,
        Set<String> roleCodes,
        Set<Long> roleIds,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        Set<String> roleCodes = new TreeSet<>();
        Set<Long> roleIds = new TreeSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getCode() != null) {
                    roleCodes.add(role.getCode());
                }
                if (role.getId() != null) {
                    roleIds.add(role.getId());
                }
            }
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getDepartmentId(),
                user.getLocale(),
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                Collections.unmodifiableSet(roleCodes),
                Collections.unmodifiableSet(roleIds),
                user.getCreatedAt()
        );
    }
}
