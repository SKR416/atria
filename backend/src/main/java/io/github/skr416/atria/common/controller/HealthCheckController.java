package io.github.skr416.atria.common.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * System health check and trilingual i18n verification endpoints for Phase 0.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "System & Health", description = "Health check and trilingual i18n verification endpoints")
public class HealthCheckController {

    private final MessageSource messageSource;

    @GetMapping("/health")
    @Operation(summary = "System health check", description = "Returns service status, UTC timestamp, and localized greeting")
    public ApiResponse<Map<String, Object>> health() {
        Locale locale = LocaleContextHolder.getLocale();
        String welcome = messageSource.getMessage("system.welcome", null, locale);
        String successMsg = messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, locale);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "atria-backend");
        payload.put("version", "0.1.0-SNAPSHOT");
        payload.put("locale", locale.toLanguageTag());
        payload.put("welcome", welcome);
        payload.put("serverTimeUtc", Instant.now().toString());

        return ApiResponse.ok(payload, successMsg);
    }

    @GetMapping("/i18n/test")
    @Operation(summary = "Trilingual i18n & ErrorCode verification", description = "Tests localized responses and BusinessException translation across zh-TW, en-US, and ja-JP")
    public ApiResponse<Map<String, String>> testI18n(
            @RequestParam(name = "error", defaultValue = "false") boolean triggerError) {
        if (triggerError) {
            throw new BusinessException(ErrorCode.WF_CANNOT_REVOKE_APPROVED);
        }

        Locale locale = LocaleContextHolder.getLocale();
        String welcome = messageSource.getMessage("system.welcome", null, locale);
        String successMsg = messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, locale);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("resolvedLocale", locale.toLanguageTag());
        data.put("greeting", welcome);
        return ApiResponse.ok(data, successMsg);
    }
}
