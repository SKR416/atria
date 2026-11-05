package io.github.skr416.atria.attendance.controller;

import io.github.skr416.atria.attendance.dto.calendar.GenerateYearCalendarRequest;
import io.github.skr416.atria.attendance.dto.calendar.PunchRuleResponse;
import io.github.skr416.atria.attendance.dto.calendar.PunchRuleUpdateRequest;
import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayResponse;
import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayUpdateRequest;
import io.github.skr416.atria.attendance.dto.close.BlockingPendingRequestDto;
import io.github.skr416.atria.attendance.dto.close.MonthlyCloseRequest;
import io.github.skr416.atria.attendance.dto.close.MonthlyCloseResponse;
import io.github.skr416.atria.attendance.dto.close.MonthlyReopenRequest;
import io.github.skr416.atria.attendance.service.MonthlyCloseService;
import io.github.skr416.atria.attendance.service.WorkCalendarService;
import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.common.api.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.List;

/**
 * REST controller exposing regional work calendars, punch rules, and monthly close management
 * (ADR-0003, ADR-0004 & ADR-0008).
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance - Calendars & Monthly Close", description = "Multi-region statutory calendars, punch rules, and monthly close locks")
public class WorkCalendarController {

    private final WorkCalendarService workCalendarService;
    private final MonthlyCloseService monthlyCloseService;
    private final MessageSource messageSource;

    private String successMessage() {
        return messageSource.getMessage(ErrorCode.SUCCESS.getMessageKey(), null, LocaleContextHolder.getLocale());
    }

    @GetMapping("/calendars")
    @PreAuthorize("hasAuthority('att:calendar:read')")
    @Operation(summary = "Get calendar days", description = "Retrieves work calendar days in date range; auto-initializes statutory days if uninitialized")
    public ApiResponse<List<WorkCalendarDayResponse>> getCalendarDays(
            @RequestParam(defaultValue = "TW") String calendarCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<WorkCalendarDayResponse> days = workCalendarService.getCalendarDays(calendarCode, startDate, endDate);
        return ApiResponse.ok(days, successMessage());
    }

    @PostMapping("/calendars/generate-year")
    @PreAuthorize("hasAuthority('att:calendar:write')")
    @Operation(summary = "Generate annual work calendar", description = "Generates all days of a year using the regional statutory holiday template")
    public ApiResponse<List<WorkCalendarDayResponse>> generateYear(
            @Valid @RequestBody GenerateYearCalendarRequest req
    ) {
        List<WorkCalendarDayResponse> days = workCalendarService.generateYear(req);
        return ApiResponse.ok(days, successMessage());
    }

    @PutMapping("/calendars/{calendarCode}/days/{workDate}")
    @PreAuthorize("hasAuthority('att:calendar:write')")
    @Operation(summary = "Upsert calendar day", description = "Creates or updates a single work calendar day entry")
    public ApiResponse<WorkCalendarDayResponse> upsertDay(
            @PathVariable String calendarCode,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @Valid @RequestBody WorkCalendarDayUpdateRequest req
    ) {
        WorkCalendarDayResponse day = workCalendarService.upsertDay(calendarCode, workDate, req);
        return ApiResponse.ok(day, successMessage());
    }

    @GetMapping("/punch-rules")
    @PreAuthorize("hasAuthority('att:calendar:read')")
    @Operation(summary = "List all punch rules", description = "Retrieves rounding, grace period, and cutoff rules for all regions")
    public ApiResponse<List<PunchRuleResponse>> getAllPunchRules() {
        List<PunchRuleResponse> rules = workCalendarService.getAllPunchRules();
        return ApiResponse.ok(rules, successMessage());
    }

    @GetMapping("/punch-rules/{calendarCode}")
    @PreAuthorize("hasAuthority('att:calendar:read')")
    @Operation(summary = "Get punch rule", description = "Retrieves punch rounding and cutoff rule for a specific region")
    public ApiResponse<PunchRuleResponse> getPunchRule(@PathVariable String calendarCode) {
        PunchRuleResponse rule = workCalendarService.getPunchRule(calendarCode);
        return ApiResponse.ok(rule, successMessage());
    }

    @PutMapping("/punch-rules/{calendarCode}")
    @PreAuthorize("hasAuthority('att:calendar:write')")
    @Operation(summary = "Update punch rule", description = "Updates punch rounding, grace period, and cutoff rules for a region")
    public ApiResponse<PunchRuleResponse> updatePunchRule(
            @PathVariable String calendarCode,
            @Valid @RequestBody PunchRuleUpdateRequest req
    ) {
        PunchRuleResponse rule = workCalendarService.updatePunchRule(calendarCode, req);
        return ApiResponse.ok(rule, successMessage());
    }

    @GetMapping("/monthly-closes")
    @PreAuthorize("hasAnyAuthority('att:monthly-close:manage', 'att:calendar:read')")
    @Operation(summary = "List monthly closes", description = "Lists monthly closing lock status records, optionally filtered by calendar code")
    public ApiResponse<List<MonthlyCloseResponse>> listMonthlyCloses(
            @RequestParam(required = false) String calendarCode
    ) {
        List<MonthlyCloseResponse> list = monthlyCloseService.listMonthlyCloses(calendarCode);
        return ApiResponse.ok(list, successMessage());
    }

    @GetMapping("/monthly-closes/blocking-requests")
    @PreAuthorize("hasAuthority('att:monthly-close:manage')")
    @Operation(summary = "Check blocking pending requests", description = "Inspects unresolved attendance requests blocking monthly closing")
    public ApiResponse<List<BlockingPendingRequestDto>> checkBlockingRequests(
            @RequestParam String calendarCode,
            @RequestParam String yearMonth
    ) {
        List<BlockingPendingRequestDto> list = monthlyCloseService.checkBlockingRequests(calendarCode, yearMonth);
        return ApiResponse.ok(list, successMessage());
    }

    @PostMapping("/monthly-closes/close")
    @PreAuthorize("hasAuthority('att:monthly-close:manage')")
    @Operation(summary = "Close monthly attendance", description = "Executes monthly closing lock on a calendar region and year-month")
    public ApiResponse<MonthlyCloseResponse> closeMonth(
            @Valid @RequestBody MonthlyCloseRequest req
    ) {
        MonthlyCloseResponse res = monthlyCloseService.closeMonth(req);
        return ApiResponse.ok(res, successMessage());
    }

    @PostMapping("/monthly-closes/reopen")
    @PreAuthorize("hasAuthority('att:monthly-close:manage')")
    @Operation(summary = "Reopen monthly attendance", description = "Reopens a previously locked monthly period")
    public ApiResponse<MonthlyCloseResponse> reopenMonth(
            @Valid @RequestBody MonthlyReopenRequest req
    ) {
        MonthlyCloseResponse res = monthlyCloseService.reopenMonth(req);
        return ApiResponse.ok(res, successMessage());
    }
}
