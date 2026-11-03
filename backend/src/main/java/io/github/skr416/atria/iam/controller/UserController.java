package io.github.skr416.atria.iam.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.api.PageResult;
import io.github.skr416.atria.iam.dto.user.AdminResetPasswordRequest;
import io.github.skr416.atria.iam.dto.user.UserCreateRequest;
import io.github.skr416.atria.iam.dto.user.UserResponse;
import io.github.skr416.atria.iam.dto.user.UserUpdateRequest;
import io.github.skr416.atria.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam/users")
@RequiredArgsConstructor
@Tag(name = "IAM - Users", description = "User management, query with data scope, password reset and unlock")
public class UserController {

    private final UserService userService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:user:read')")
    @Operation(summary = "List users", description = "Paged user query filtered by caller data scope, department, and keyword")
    public ApiResponse<PageResult<UserResponse>> listUsers(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam(name = "keyword", required = false) String keyword) {
        PageResult<UserResponse> result = userService.listUsers(page, size, departmentId, keyword);
        return ApiResponse.ok(result, getSuccessMessage());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:user:read')")
    @Operation(summary = "Get user by ID", description = "Retrieves user details verified against data scope")
    public ApiResponse<UserResponse> getUserById(@PathVariable("id") Long id) {
        UserResponse user = userService.getUserById(id);
        return ApiResponse.ok(user, getSuccessMessage());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('iam:user:write')")
    @Operation(summary = "Create user", description = "Creates a new user account with initial roles and password")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest req) {
        UserResponse user = userService.createUser(req);
        return ApiResponse.ok(user, getSuccessMessage());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:user:write')")
    @Operation(summary = "Update user", description = "Updates user profile, department, status, and assigned roles")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateRequest req) {
        UserResponse user = userService.updateUser(id, req);
        return ApiResponse.ok(user, getSuccessMessage());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:user:write')")
    @Operation(summary = "Delete user", description = "Soft deletes a user account and revokes active sessions")
    public ApiResponse<Void> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok(null, getSuccessMessage());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('iam:user:write')")
    @Operation(summary = "Admin reset password", description = "Resets user password to temporary password and invalidates tokens")
    public ApiResponse<Void> adminResetPassword(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminResetPasswordRequest req) {
        userService.adminResetPassword(id, req);
        return ApiResponse.ok(null, getSuccessMessage());
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('iam:user:write')")
    @Operation(summary = "Unlock user", description = "Resets failed login attempts and unlocks locked user account")
    public ApiResponse<Void> unlockUser(@PathVariable("id") Long id) {
        userService.unlockUser(id);
        return ApiResponse.ok(null, getSuccessMessage());
    }

    private String getSuccessMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }
}
