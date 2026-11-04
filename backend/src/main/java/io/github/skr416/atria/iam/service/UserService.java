package io.github.skr416.atria.iam.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.api.PageResult;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import io.github.skr416.atria.iam.datascope.DataScope;
import io.github.skr416.atria.iam.datascope.DataScopeContext;
import io.github.skr416.atria.iam.dto.user.AdminResetPasswordRequest;
import io.github.skr416.atria.iam.dto.user.UserCreateRequest;
import io.github.skr416.atria.iam.dto.user.UserResponse;
import io.github.skr416.atria.iam.dto.user.UserUpdateRequest;
import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.RoleRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @DataScope(deptField = "departmentId", userField = "id")
    @Transactional(readOnly = true)
    public PageResult<UserResponse> listUsers(int page, int size, Long departmentId, String keyword) {
        Specification<User> spec = Specification.where(DataScopeContext.toSpecification());
        if (departmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("departmentId"), departmentId));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("displayName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            ));
        }

        int pageIndex = Math.max(0, page - 1);
        PageRequest pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> userPage = userRepository.findAll(spec, pageable);
        return PageResult.from(userPage.map(UserResponse::from));
    }

    @DataScope(deptField = "departmentId", userField = "id")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findWithAuthoritiesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        DataScopeContext.Filter f = DataScopeContext.current();
        if (f != null && !f.permitsUser(user.getId(), user.getDepartmentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return UserResponse.from(user);
    }

    @AuditLog(action = "USER_CREATE", targetType = "USER")
    @Transactional
    public UserResponse createUser(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (req.email() != null && !req.email().isBlank()) {
            if (userRepository.existsByEmailIgnoreCase(req.email().trim())) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }
        if (!departmentRepository.existsById(req.departmentId())) {
            throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
        }

        Set<Role> roles = new HashSet<>();
        if (req.roleIds() != null && !req.roleIds().isEmpty()) {
            List<Role> foundRoles = roleRepository.findAllById(req.roleIds());
            if (foundRoles.size() != req.roleIds().size()) {
                throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
            }
            roles.addAll(foundRoles);
        }

        String passwordHash = passwordEncoder.encode(req.initialPassword());
        boolean mustChangePassword = req.mustChangePassword() == null || Boolean.TRUE.equals(req.mustChangePassword());

        User user = new User();
        user.setUsername(req.username().trim());
        user.setEmail(req.email() != null && !req.email().isBlank() ? req.email().trim() : null);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(req.displayName().trim());
        user.setDepartmentId(req.departmentId());
        user.setHireDate(req.hireDate());
        user.setLocale(req.locale() != null && !req.locale().isBlank() ? req.locale().trim() : "zh-TW");
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        user.setMustChangePassword(mustChangePassword);
        user.setTokenVersion(0L);
        user.setRoles(roles);

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    @AuditLog(action = "USER_UPDATE", targetType = "USER")
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest req) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newEmail = req.email() != null && !req.email().isBlank() ? req.email().trim() : null;
        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }

        if (!departmentRepository.existsById(req.departmentId())) {
            throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
        }

        user.setEmail(newEmail);
        user.setDisplayName(req.displayName().trim());
        user.setDepartmentId(req.departmentId());
        user.setHireDate(req.hireDate());
        if (req.locale() != null && !req.locale().isBlank()) {
            user.setLocale(req.locale().trim());
        }

        if (req.enabled() != null) {
            if (!req.enabled() && user.isEnabled()) {
                user.setTokenVersion(user.getTokenVersion() + 1);
            }
            user.setEnabled(req.enabled());
        }

        if (req.roleIds() != null) {
            if (req.roleIds().isEmpty()) {
                user.getRoles().clear();
            } else {
                List<Role> foundRoles = roleRepository.findAllById(req.roleIds());
                if (foundRoles.size() != req.roleIds().size()) {
                    throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
                }
                user.getRoles().clear();
                user.getRoles().addAll(foundRoles);
            }
        }

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    @AuditLog(action = "USER_DELETE", targetType = "USER")
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setDeleted(true);
        user.setEnabled(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    @AuditLog(action = "USER_RESET_PASSWORD", targetType = "USER")
    @Transactional
    public void adminResetPassword(Long id, AdminResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(req.temporaryPassword()));
        boolean mustChangePassword = req.mustChangePassword() == null || Boolean.TRUE.equals(req.mustChangePassword());
        user.setMustChangePassword(mustChangePassword);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setPasswordChangedAt(Instant.now());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    @AuditLog(action = "USER_UNLOCK", targetType = "USER")
    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
