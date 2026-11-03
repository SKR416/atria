package io.github.skr416.atria.iam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.iam.model.DataScopeType;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.Permission;
import io.github.skr416.atria.iam.model.Role;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Filter intercepting incoming requests to authenticate JWT Bearer tokens and enforce security gates (ADR-0004).
 * Rejects refresh tokens in the Bearer header and enforces the mandatory password change gate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PASSWORD_CHANGE_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me"
    );

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<JwtTokenService.TokenClaims> claimsOpt = jwtTokenService.safeParseClaims(token);
        if (claimsOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtTokenService.TokenClaims claims = claimsOpt.get();

        // Rejects "refresh" tokens used in the Bearer Authorization header!
        if (!JwtTokenService.TYPE_ACCESS.equals(claims.tokenType()) &&
                !JwtTokenService.TYPE_PWD_CHANGE.equals(claims.tokenType())) {
            log.debug("Rejected Bearer token with unauthorized tokenType: {}", claims.tokenType());
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> userOpt = userRepository.findWithAuthoritiesById(claims.userId());
        if (userOpt.isEmpty()) {
            log.debug("User with ID {} from token claims not found in database", claims.userId());
            filterChain.doFilter(request, response);
            return;
        }

        User user = userOpt.get();
        Instant now = Instant.now();

        if (!user.isEnabled() || user.isLocked(now) || user.getTokenVersion() != claims.tokenVersion()) {
            log.debug("User {} state rejected: enabled={}, locked={}, tokenVersionMatches={}",
                    user.getUsername(), user.isEnabled(), user.isLocked(now), user.getTokenVersion() == claims.tokenVersion());
            filterChain.doFilter(request, response);
            return;
        }

        // Mandatory Password Change Gate (ADR-0004)
        boolean mustChangePassword = user.isMustChangePassword() || JwtTokenService.TYPE_PWD_CHANGE.equals(claims.tokenType());
        if (mustChangePassword && !isAllowedPasswordChangePath(request)) {
            log.warn("User '{}' must change password; access to {} blocked by mandatory password change gate",
                    user.getUsername(), request.getRequestURI());
            writePasswordChangeRequiredResponse(response);
            return;
        }

        // Compute effective DataScopeType across all assigned roles
        DataScopeType effectiveDataScope = DataScopeType.SELF_ONLY;
        Set<String> roleCodes = new LinkedHashSet<>();
        Set<String> permissionCodes = new LinkedHashSet<>();

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getCode() != null) {
                    roleCodes.add(role.getCode());
                }
                if (role.getDataScope() != null) {
                    effectiveDataScope = DataScopeType.broader(effectiveDataScope, role.getDataScope());
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

        // Look up department treePath
        String departmentTreePath = null;
        if (user.getDepartmentId() != null) {
            departmentTreePath = departmentRepository.findById(user.getDepartmentId())
                    .map(Department::getTreePath)
                    .orElse(null);
        }

        AtriaUserPrincipal principal = AtriaUserPrincipal.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .departmentId(user.getDepartmentId())
                .departmentTreePath(departmentTreePath)
                .locale(user.getLocale())
                .enabled(user.isEnabled())
                .mustChangePassword(user.isMustChangePassword())
                .tokenVersion(user.getTokenVersion())
                .dataScope(effectiveDataScope)
                .roleCodes(roleCodes)
                .permissionCodes(permissionCodes)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedPasswordChangePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (ALLOWED_PASSWORD_CHANGE_PATHS.contains(servletPath)) {
            return true;
        }
        String requestUri = request.getRequestURI();
        return ALLOWED_PASSWORD_CHANGE_PATHS.contains(requestUri);
    }

    private void writePasswordChangeRequiredResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage(
                ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED.getMessageKey(),
                null,
                ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED.getMessageKey(),
                locale
        );

        ApiResponse<Void> errorResponse = ApiResponse.error(ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
