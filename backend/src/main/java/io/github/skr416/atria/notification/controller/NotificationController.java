package io.github.skr416.atria.notification.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.iam.security.SecurityUtils;
import io.github.skr416.atria.notification.dto.DigestBatchResultDto;
import io.github.skr416.atria.notification.dto.DigestTriggerRequest;
import io.github.skr416.atria.notification.dto.NotificationResponse;
import io.github.skr416.atria.notification.service.LeaveBalanceDigestJob;
import io.github.skr416.atria.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * REST controller for in-app notification queries, mark-as-read operations,
 * and HR administrator manual leave digest triggering (ADR-0008 Decisions 8 & 10).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Endpoints for user in-app notifications and monthly leave digest")
public class NotificationController {

    private final NotificationService notificationService;
    private final LeaveBalanceDigestJob leaveBalanceDigestJob;

    @GetMapping
    @Operation(summary = "List current user notifications, optionally unread only")
    public ApiResponse<List<NotificationResponse>> listMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        List<NotificationResponse> list = notificationService.listMyNotifications(userId, unreadOnly);
        return ApiResponse.ok(list);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications for current user")
    public ApiResponse<Map<String, Long>> countUnread() {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        long count = notificationService.countUnread(userId);
        return ApiResponse.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        NotificationResponse response = notificationService.markAsRead(id, userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read for current user")
    public ApiResponse<Map<String, Integer>> markAllAsRead() {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        int count = notificationService.markAllAsRead(userId);
        return ApiResponse.ok(Map.of("updatedCount", count));
    }

    @PostMapping("/digest/trigger")
    @PreAuthorize("hasAuthority('ntf:digest:trigger')")
    @Operation(summary = "Manually trigger monthly leave balance digest batch job")
    public ApiResponse<DigestBatchResultDto> triggerMonthlyDigest(
            @RequestBody(required = false) DigestTriggerRequest request) {
        String periodYearMonth = request != null && request.periodYearMonth() != null && !request.periodYearMonth().isBlank()
                ? request.periodYearMonth().trim()
                : YearMonth.now().toString();
        LocalDate asOfDate = request != null && request.asOfDate() != null
                ? request.asOfDate()
                : LocalDate.now();
        DigestBatchResultDto result = leaveBalanceDigestJob.triggerMonthlyDigest(periodYearMonth, asOfDate);
        return ApiResponse.ok(result);
    }
}
