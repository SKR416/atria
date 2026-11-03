package io.github.skr416.atria.iam.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.iam.dto.auth.ChangePasswordRequest;
import io.github.skr416.atria.iam.dto.auth.CurrentUserProfileResponse;
import io.github.skr416.atria.iam.dto.auth.LoginRequest;
import io.github.skr416.atria.iam.dto.auth.LoginResponse;
import io.github.skr416.atria.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing authentication, password change, token refresh, logout, and profile endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication, token management, password change, and current user profile")
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;

    private String successMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user credentials and issues access token and HttpOnly refresh cookie")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        LoginResponse response = authService.login(req, httpReq, httpResp);
        return ApiResponse.ok(response, successMessage());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Updates password for currently authenticated user and revokes prior tokens")
    public ApiResponse<LoginResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        LoginResponse response = authService.changePassword(req, httpReq, httpResp);
        return ApiResponse.ok(response, successMessage());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Issues a new access token and rotates the HttpOnly refresh token cookie")
    public ApiResponse<LoginResponse> refresh(
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        LoginResponse response = authService.refresh(httpReq, httpResp);
        return ApiResponse.ok(response, successMessage());
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidates active user sessions by incrementing tokenVersion and clears the refresh cookie")
    public ApiResponse<Void> logout(
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        authService.logout(httpReq, httpResp);
        return ApiResponse.ok(null, successMessage());
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile", description = "Returns profile, assigned roles, permissions, and effective data scope for the caller")
    public ApiResponse<CurrentUserProfileResponse> me() {
        CurrentUserProfileResponse profile = authService.me();
        return ApiResponse.ok(profile, successMessage());
    }
}
