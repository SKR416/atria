package io.github.skr416.atria.iam.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.common.util.ClientIpResolver;
import io.github.skr416.atria.iam.dto.auth.ChangePasswordRequest;
import io.github.skr416.atria.iam.dto.auth.CurrentUserProfileResponse;
import io.github.skr416.atria.iam.dto.auth.LoginRequest;
import io.github.skr416.atria.iam.dto.auth.LoginResponse;
import io.github.skr416.atria.iam.model.AuditLogEntry;
import io.github.skr416.atria.iam.model.DataScopeType;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.AuditLogRepository;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.JwtTokenService;
import io.github.skr416.atria.iam.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Core authentication service handling login, token rotation, DBA emergency password recovery,
 * brute-force lockout, and password change (ADR-0004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${atria.security.refresh-cookie.name:atria_refresh}")
    private String refreshCookieName;

    @Value("${atria.security.refresh-cookie.secure:true}")
    private boolean refreshCookieSecure;

    @Value("${atria.security.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${atria.security.lockout.lock-minutes:15}")
    private int lockMinutes;

    /**
     * Authenticates credentials with support for DBA Emergency Recovery (ADR-0004 Section 3).
     * If an unhashed/noop password was entered by DBA, login succeeds once, flags mustChangePassword,
     * and forces the user to the password change endpoint.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest req, HttpServletRequest httpReq, HttpServletResponse httpResp) {
        User user = userRepository.findWithAuthoritiesByUsername(req.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        // Password verification & DBA Emergency Recovery
        boolean matched = false;
        boolean dbaRecoveryMatched = false;
        String storedHash = user.getPasswordHash();

        if (storedHash != null && storedHash.startsWith("{bcrypt}")) {
            // Case A: Standard BCrypt encoded password
            matched = passwordEncoder.matches(req.password(), storedHash);
        } else if (storedHash != null) {
            // Case B: DBA Emergency Recovery (missing {bcrypt} prefix, e.g. {noop}Temp123456 or raw plaintext)
            if (storedHash.startsWith("{noop}")) {
                matched = storedHash.substring("{noop}".length()).equals(req.password());
            } else if (!storedHash.startsWith("{")) {
                matched = storedHash.equals(req.password());
            } else {
                matched = passwordEncoder.matches(req.password(), storedHash);
            }
            if (matched) {
                dbaRecoveryMatched = true;
                user.setMustChangePassword(true);
                log.warn("User '{}' authenticated via DBA Emergency Recovery; mandatory password change enforced", user.getUsername());
            }
        }

        if (!matched) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            boolean justLocked = false;
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(lockMinutes)));
                justLocked = true;
                log.warn("Account '{}' locked until {} after {} consecutive failed login attempts",
                        user.getUsername(), user.getLockedUntil(), attempts);
            }
            userRepository.save(user);

            if (justLocked) {
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Reset failed login counters on successful authentication
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        // Record audit log entry
        AuditLogEntry audit = new AuditLogEntry();
        audit.setAction(user.isMustChangePassword() ? "AUTH_LOGIN_DBA_RECOVERY" : "AUTH_LOGIN");
        audit.setActorId(user.getId());
        audit.setActorUsername(user.getUsername());
        audit.setTargetType("USER");
        audit.setTargetId(String.valueOf(user.getId()));
        audit.setDetail(dbaRecoveryMatched ? "Emergency login via DBA plaintext/noop password; password change required" : "Successful login");
        audit.setClientIp(ClientIpResolver.resolve(httpReq));
        audit.setCreatedAt(now);
        auditLogRepository.save(audit);

        // Issue appropriate access token and manage refresh cookie
        String accessToken;
        long expiresInSeconds;

        if (user.isMustChangePassword()) {
            accessToken = jwtTokenService.generatePasswordChangeToken(user.getId(), user.getUsername(), user.getTokenVersion());
            expiresInSeconds = jwtTokenService.getPasswordChangeTtlSeconds();
            httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        } else {
            accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), user.getTokenVersion());
            expiresInSeconds = jwtTokenService.getAccessTtlSeconds();
            String refreshToken = jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), user.getTokenVersion());
            httpResp.addHeader(HttpHeaders.SET_COOKIE, createRefreshCookie(refreshToken, Duration.ofDays(jwtTokenService.getRefreshTtlDays())).toString());
        }

        CurrentUserProfileResponse profile = buildUserProfile(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .mustChangePassword(user.isMustChangePassword())
                .user(profile)
                .build();
    }

    /**
     * Changes current user's password, encodes with {bcrypt}, resets mustChangePassword,
     * increments tokenVersion to revoke old sessions, and issues a fresh session.
     */
    @Transactional
    public LoginResponse changePassword(ChangePasswordRequest req, HttpServletRequest httpReq, HttpServletResponse httpResp) {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findWithAuthoritiesById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!verifyPassword(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(now);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Record audit log entry
        AuditLogEntry audit = new AuditLogEntry();
        audit.setAction("AUTH_CHANGE_PASSWORD");
        audit.setActorId(user.getId());
        audit.setActorUsername(user.getUsername());
        audit.setTargetType("USER");
        audit.setTargetId(String.valueOf(user.getId()));
        audit.setDetail("Password updated successfully; previous tokens invalidated");
        audit.setClientIp(ClientIpResolver.resolve(httpReq));
        audit.setCreatedAt(now);
        auditLogRepository.save(audit);

        // Issue fresh normal access token and rotated refresh cookie
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), user.getTokenVersion());
        long expiresInSeconds = jwtTokenService.getAccessTtlSeconds();
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), user.getTokenVersion());
        httpResp.addHeader(HttpHeaders.SET_COOKIE, createRefreshCookie(refreshToken, Duration.ofDays(jwtTokenService.getRefreshTtlDays())).toString());

        CurrentUserProfileResponse profile = buildUserProfile(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .mustChangePassword(false)
                .user(profile)
                .build();
    }

    /**
     * Rotates access and refresh tokens using the HttpOnly refresh cookie.
     */
    @Transactional
    public LoginResponse refresh(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String refreshToken = extractRefreshCookie(httpReq);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Optional<JwtTokenService.TokenClaims> claimsOpt = jwtTokenService.safeParseClaims(refreshToken);
        if (claimsOpt.isEmpty()) {
            httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        JwtTokenService.TokenClaims claims = claimsOpt.get();
        if (!JwtTokenService.TYPE_REFRESH.equals(claims.tokenType())) {
            httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findWithAuthoritiesById(claims.userId())
                .orElseThrow(() -> {
                    httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
                    return new BusinessException(ErrorCode.UNAUTHORIZED);
                });

        Instant now = Instant.now();
        if (!user.isEnabled() || user.isLocked(now) || user.getTokenVersion() != claims.tokenVersion() || user.isMustChangePassword()) {
            httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Issue new access token and rotate refresh token
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), user.getTokenVersion());
        long expiresInSeconds = jwtTokenService.getAccessTtlSeconds();
        String newRefreshToken = jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), user.getTokenVersion());
        httpResp.addHeader(HttpHeaders.SET_COOKIE, createRefreshCookie(newRefreshToken, Duration.ofDays(jwtTokenService.getRefreshTtlDays())).toString());

        CurrentUserProfileResponse profile = buildUserProfile(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(expiresInSeconds)
                .mustChangePassword(user.isMustChangePassword())
                .user(profile)
                .build();
    }

    /**
     * Invalidates current session by incrementing user's tokenVersion and clearing the refresh cookie.
     */
    @Transactional
    public void logout(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        SecurityUtils.currentPrincipal().ifPresent(principal -> {
            userRepository.findById(principal.getUserId()).ifPresent(user -> {
                user.setTokenVersion(user.getTokenVersion() + 1);
                userRepository.save(user);

                AuditLogEntry audit = new AuditLogEntry();
                audit.setAction("AUTH_LOGOUT");
                audit.setActorId(user.getId());
                audit.setActorUsername(user.getUsername());
                audit.setTargetType("USER");
                audit.setTargetId(String.valueOf(user.getId()));
                audit.setDetail("User logged out; token version incremented");
                audit.setClientIp(ClientIpResolver.resolve(httpReq));
                audit.setCreatedAt(Instant.now());
                auditLogRepository.save(audit);
            });
        });
        httpResp.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
    }

    /**
     * Retrieves the profile and effective security scope of the currently authenticated principal.
     */
    @Transactional(readOnly = true)
    public CurrentUserProfileResponse me() {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findWithAuthoritiesById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return buildUserProfile(user);
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || rawPassword == null) {
            return false;
        }
        if (storedHash.startsWith("{bcrypt}")) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        if (storedHash.startsWith("{noop}")) {
            return storedHash.substring("{noop}".length()).equals(rawPassword);
        }
        if (!storedHash.startsWith("{")) {
            return storedHash.equals(rawPassword);
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    private CurrentUserProfileResponse buildUserProfile(User user) {
        DataScopeType effectiveDataScope = DataScopeType.SELF_ONLY;
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getDataScope() != null) {
                    effectiveDataScope = DataScopeType.broader(effectiveDataScope, role.getDataScope());
                }
            }
        }
        String departmentTreePath = null;
        String departmentTimezone = "Asia/Taipei";
        String departmentCalendarCode = "TW";
        if (user.getDepartmentId() != null) {
            Optional<Department> deptOpt = departmentRepository.findById(user.getDepartmentId());
            if (deptOpt.isPresent()) {
                Department dept = deptOpt.get();
                departmentTreePath = dept.getTreePath();
                if (dept.getTimezone() != null && !dept.getTimezone().isBlank()) {
                    departmentTimezone = dept.getTimezone();
                }
                if (dept.getCalendarCode() != null && !dept.getCalendarCode().isBlank()) {
                    departmentCalendarCode = dept.getCalendarCode();
                }
            }
        }
        return CurrentUserProfileResponse.from(
                user,
                effectiveDataScope,
                departmentTreePath,
                departmentTimezone,
                departmentCalendarCode
        );
    }

    private ResponseCookie createRefreshCookie(String refreshToken, Duration maxAge) {
        return ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
