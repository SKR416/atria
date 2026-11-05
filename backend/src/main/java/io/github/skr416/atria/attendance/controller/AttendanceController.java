package io.github.skr416.atria.attendance.controller;

import io.github.skr416.atria.attendance.dto.punch.BotPunchRequest;
import io.github.skr416.atria.attendance.dto.punch.MonthlyOverviewResponse;
import io.github.skr416.atria.attendance.dto.punch.TodayAttendanceSummaryResponse;
import io.github.skr416.atria.attendance.dto.punch.WebPunchRequest;
import io.github.skr416.atria.attendance.service.DailySettlementJob;
import io.github.skr416.atria.attendance.service.PunchService;
import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.iam.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Controller exposing attendance clock-in/out, daily summary, monthly overview,
 * third-party bot punch proxy, and daily settlement execution endpoints (ADR-0003 & ADR-0008).
 */
@Tag(name = "Attendance Management", description = "Endpoints for employee clock-in, punch logs, calendar overview, and daily settlement")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final PunchService punchService;
    private final DailySettlementJob dailySettlementJob;

    @Operation(summary = "Get today's attendance summary for current authenticated employee")
    @GetMapping("/today")
    @PreAuthorize("hasAuthority('att:punch:self')")
    public ApiResponse<TodayAttendanceSummaryResponse> getTodaySummary() {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(punchService.getTodaySummary(userId));
    }

    @Operation(summary = "Web self-service clock-in / clock-out")
    @PostMapping("/punch")
    @PreAuthorize("hasAuthority('att:punch:self')")
    public ApiResponse<TodayAttendanceSummaryResponse> punchWeb(
            @Valid @RequestBody(required = false) WebPunchRequest req,
            HttpServletRequest httpReq
    ) {
        return ApiResponse.ok(punchService.punchWeb(req, httpReq));
    }

    @Operation(summary = "Third-party Bot proxy punch endpoint (authenticated via HMAC-SHA256 headers)")
    @PostMapping("/punch/bot")
    public ApiResponse<TodayAttendanceSummaryResponse> punchBot(
            @Valid @RequestBody BotPunchRequest req,
            @RequestHeader(value = "X-Atria-Bot-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Atria-Bot-Signature", required = false) String signature,
            HttpServletRequest httpReq
    ) {
        return ApiResponse.ok(punchService.punchBot(req, timestamp, signature, httpReq));
    }

    @Operation(summary = "Get monthly calendar overview and attendance KPI counters for current employee")
    @GetMapping("/monthly-overview")
    @PreAuthorize("hasAnyAuthority('att:record:read', 'att:punch:self')")
    public ApiResponse<MonthlyOverviewResponse> getMonthlyOverview(
            @RequestParam(required = false) String yearMonth
    ) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(punchService.getMonthlyOverview(userId, yearMonth));
    }

    @Operation(summary = "Trigger daily attendance settlement to finalize unclocked / incomplete records")
    @PostMapping("/settlement/run")
    @PreAuthorize("hasAuthority('att:monthly-close:manage')")
    public ApiResponse<Integer> triggerDailySettlement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate
    ) {
        LocalDate targetDate = workDate != null ? workDate : LocalDate.now().minusDays(1);
        int settledCount = dailySettlementJob.settleWorkDate(targetDate);
        return ApiResponse.ok(settledCount);
    }
}
