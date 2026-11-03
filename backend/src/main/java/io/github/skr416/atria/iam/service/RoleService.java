package io.github.skr416.atria.iam.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import io.github.skr416.atria.iam.dto.role.PermissionResponse;
import io.github.skr416.atria.iam.dto.role.RoleRequest;
import io.github.skr416.atria.iam.dto.role.RoleResponse;
import io.github.skr416.atria.iam.model.DataScopeType;
import io.github.skr416.atria.iam.model.Permission;
import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.repository.PermissionRepository;
import io.github.skr416.atria.iam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Service managing RBAC roles, permission assignments, and data scope settings (ADR-0004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Lists all roles with their eager permissions.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        Locale locale = LocaleContextHolder.getLocale();
        return roleRepository.findAllByOrderByIdAsc().stream()
                .map(role -> toRoleResponse(role, locale))
                .toList();
    }

    /**
     * Finds a single role by ID with permissions eagerly fetched.
     */
    @Transactional(readOnly = true)
    public RoleResponse getRole(Long id) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        return toRoleResponse(role, LocaleContextHolder.getLocale());
    }

    /**
     * Lists all defined permissions in the system, grouped/ordered by module and code.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        Locale locale = LocaleContextHolder.getLocale();
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(perm -> toPermissionResponse(perm, locale))
                .toList();
    }

    /**
     * Creates a new custom role.
     */
    @Transactional
    @AuditLog(action = "ROLE_CREATE", targetType = "ROLE")
    public RoleResponse createRole(RoleRequest req) {
        if (roleRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }

        Set<Permission> permissions = new HashSet<>();
        if (req.permissionIds() != null && !req.permissionIds().isEmpty()) {
            permissions.addAll(permissionRepository.findAllById(req.permissionIds()));
        }

        Role role = new Role();
        role.setCode(req.code());
        role.setName(req.name());
        role.setDataScope(req.dataScope() != null ? req.dataScope() : DataScopeType.SELF_ONLY);
        role.setBuiltIn(false);
        role.setPermissions(permissions);

        role = roleRepository.save(role);
        log.info("Created role [{}] (id={})", role.getCode(), role.getId());
        return toRoleResponse(role, LocaleContextHolder.getLocale());
    }

    /**
     * Updates an existing role's name, data scope, and assigned permissions.
     * Built-in roles cannot have their code modified.
     */
    @Transactional
    @AuditLog(action = "ROLE_UPDATE", targetType = "ROLE")
    public RoleResponse updateRole(Long id, RoleRequest req) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        if (role.isBuiltIn() && !role.getCode().equals(req.code())) {
            throw new BusinessException(ErrorCode.ROLE_BUILTIN_IMMUTABLE);
        }

        if (!role.getCode().equals(req.code()) && roleRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }

        if (!role.isBuiltIn()) {
            role.setCode(req.code());
        }

        role.setName(req.name());
        if (req.dataScope() != null) {
            role.setDataScope(req.dataScope());
        }

        if (req.permissionIds() != null) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(req.permissionIds()));
            role.setPermissions(permissions);
        }

        role = roleRepository.save(role);
        log.info("Updated role [{}] (id={})", role.getCode(), role.getId());
        return toRoleResponse(role, LocaleContextHolder.getLocale());
    }

    /**
     * Soft-deletes a custom role. Built-in system roles cannot be deleted.
     */
    @Transactional
    @AuditLog(action = "ROLE_DELETE", targetType = "ROLE")
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        if (role.isBuiltIn()) {
            throw new BusinessException(ErrorCode.ROLE_BUILTIN_IMMUTABLE);
        }

        role.setDeleted(true);
        roleRepository.save(role);
        log.info("Soft-deleted role [{}] (id={})", role.getCode(), id);
    }

    private RoleResponse toRoleResponse(Role role, Locale locale) {
        Set<PermissionResponse> permissionResponses = new LinkedHashSet<>();
        Set<String> permissionCodes = new LinkedHashSet<>();

        if (role.getPermissions() != null) {
            for (Permission perm : role.getPermissions()) {
                permissionResponses.add(toPermissionResponse(perm, locale));
                permissionCodes.add(perm.getCode());
            }
        }

        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .localizedName(RoleResponse.resolveLocalizedName(role.getName(), locale))
                .dataScope(role.getDataScope())
                .builtIn(role.isBuiltIn())
                .permissions(permissionResponses)
                .permissionCodes(permissionCodes)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private PermissionResponse toPermissionResponse(Permission perm, Locale locale) {
        return PermissionResponse.builder()
                .id(perm.getId())
                .code(perm.getCode())
                .module(perm.getModule())
                .name(perm.getName())
                .localizedName(PermissionResponse.resolveLocalizedName(perm.getName(), locale))
                .build();
    }
}
