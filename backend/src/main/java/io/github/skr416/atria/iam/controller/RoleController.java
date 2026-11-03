package io.github.skr416.atria.iam.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.iam.dto.role.PermissionResponse;
import io.github.skr416.atria.iam.dto.role.RoleRequest;
import io.github.skr416.atria.iam.dto.role.RoleResponse;
import io.github.skr416.atria.iam.service.RoleService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing endpoints for Role and Permission management (ADR-0004).
 */
@RestController
@RequestMapping("/api/v1/iam")
@RequiredArgsConstructor
@Tag(name = "IAM - Roles & Permissions", description = "Role and permission management endpoints")
public class RoleController {

    private final RoleService roleService;
    private final MessageSource messageSource;

    private String successMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('iam:role:read')")
    @Operation(summary = "List roles", description = "Returns all roles with their assigned permissions")
    public ApiResponse<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleService.listRoles();
        return ApiResponse.ok(roles, successMessage());
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('iam:role:read')")
    @Operation(summary = "Get role by ID", description = "Returns details and permissions of a single role")
    public ApiResponse<RoleResponse> getRole(@PathVariable Long id) {
        RoleResponse role = roleService.getRole(id);
        return ApiResponse.ok(role, successMessage());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('iam:role:write')")
    @Operation(summary = "Create role", description = "Creates a new custom role with specified data scope and permissions")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        RoleResponse created = roleService.createRole(request);
        return ApiResponse.ok(created, successMessage());
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('iam:role:write')")
    @Operation(summary = "Update role", description = "Updates name, data scope, and permissions of a role")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        RoleResponse updated = roleService.updateRole(id, request);
        return ApiResponse.ok(updated, successMessage());
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('iam:role:write')")
    @Operation(summary = "Delete role", description = "Soft-deletes a custom role (built-in roles cannot be deleted)")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok(null, successMessage());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('iam:role:read')")
    @Operation(summary = "List permissions", description = "Returns all available system permissions grouped by module")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        List<PermissionResponse> permissions = roleService.listPermissions();
        return ApiResponse.ok(permissions, successMessage());
    }
}
