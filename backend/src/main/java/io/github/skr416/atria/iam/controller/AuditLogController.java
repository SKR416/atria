package io.github.skr416.atria.iam.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.api.PageResult;
import io.github.skr416.atria.iam.dto.role.AuditLogResponse;
import io.github.skr416.atria.iam.model.AuditLogEntry;
import io.github.skr416.atria.iam.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing administrative audit log query endpoints (ADR-0004).
 */
@Validated
@RestController
@RequestMapping("/api/v1/iam/audit-logs")
@RequiredArgsConstructor
@Tag(name = "IAM - Audit Logs", description = "Administrative operation audit logs query")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final MessageSource messageSource;

    private String successMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:audit:read')")
    @Operation(summary = "Query audit logs", description = "Returns paginated administrative audit logs sorted by createdAt DESC")
    public ApiResponse<PageResult<AuditLogResponse>> queryAuditLogs(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntry> logPage = auditLogRepository.findAll(pageable);
        Page<AuditLogResponse> responsePage = logPage.map(this::toResponse);
        return ApiResponse.ok(PageResult.from(responsePage), successMessage());
    }

    private AuditLogResponse toResponse(AuditLogEntry entry) {
        return AuditLogResponse.builder()
                .id(entry.getId())
                .actorId(entry.getActorId())
                .actorUsername(entry.getActorUsername())
                .action(entry.getAction())
                .targetType(entry.getTargetType())
                .targetId(entry.getTargetId())
                .detail(entry.getDetail())
                .clientIp(entry.getClientIp())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
