package io.github.skr416.atria.attendance.controller;

import io.github.skr416.atria.attendance.dto.leave.AnnualLeaveGrantBatchResultDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveBalanceBucketDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveDashboardResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveGrantRuleResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveGrantRuleUpsertRequest;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeUpsertRequest;
import io.github.skr416.atria.attendance.dto.leave.ManualLeaveGrantRequest;
import io.github.skr416.atria.attendance.dto.overtime.CompLeaveExpirySettlementResultDto;
import io.github.skr416.atria.attendance.dto.overtime.CompLeaveGrantResponse;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeCapRuleResponse;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeCapRuleUpsertRequest;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeRateRuleResponse;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeRateRuleUpsertRequest;
import io.github.skr416.atria.attendance.dto.request.AttendanceRequestResponse;
import io.github.skr416.atria.attendance.dto.request.LeaveRequestSubmitDto;
import io.github.skr416.atria.attendance.dto.request.OvertimeRequestSubmitDto;
import io.github.skr416.atria.attendance.dto.request.PunchCorrectionRequestSubmitDto;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.service.AnnualLeaveGrantService;
import io.github.skr416.atria.attendance.service.AttendanceRequestService;
import io.github.skr416.atria.attendance.service.CompLeaveGrantService;
import io.github.skr416.atria.attendance.service.LeaveDashboardQuery;
import io.github.skr416.atria.attendance.service.LeaveTypeService;
import io.github.skr416.atria.attendance.service.OvertimeRuleAndCalculatorService;
import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.iam.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * REST controller exposing endpoints for leave types, seniority grant rules, leave balance dashboard,
 * manual grants, overtime rate and cap rules, compensatory leave grants, and unified attendance requests
 * (ADR-0008 Decisions 3, 4, 5, 6 & 9).
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance - Leave & Overtime", description = "Endpoints for leave types, grant rules, balances, overtime rules, comp-leave grants, and attendance requests")
public class LeaveAndOvertimeController {

    private final LeaveTypeService leaveTypeService;
    private final AnnualLeaveGrantService annualLeaveGrantService;
    private final LeaveDashboardQuery leaveDashboardQuery;
    private final OvertimeRuleAndCalculatorService overtimeRuleAndCalculatorService;
    private final CompLeaveGrantService compLeaveGrantService;
    private final AttendanceRequestService attendanceRequestService;

    @Operation(summary = "List leave types")
    @GetMapping("/leave-types")
    @PreAuthorize("hasAnyAuthority('att:rule:read', 'att:calendar:read', 'att:request:self', 'att:punch:self')")
    public ApiResponse<List<LeaveTypeResponse>> listLeaveTypes(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        return ApiResponse.ok(leaveTypeService.listLeaveTypes(enabledOnly));
    }

    @Operation(summary = "Create leave type")
    @PostMapping("/leave-types")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<LeaveTypeResponse> createLeaveType(
            @Valid @RequestBody LeaveTypeUpsertRequest req
    ) {
        return ApiResponse.ok(leaveTypeService.createLeaveType(req));
    }

    @Operation(summary = "Update leave type")
    @PutMapping("/leave-types/{id}")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<LeaveTypeResponse> updateLeaveType(
            @PathVariable Long id,
            @Valid @RequestBody LeaveTypeUpsertRequest req
    ) {
        return ApiResponse.ok(leaveTypeService.updateLeaveType(id, req));
    }

    @Operation(summary = "List leave grant rules")
    @GetMapping("/leave-grant-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:read', 'att:calendar:read')")
    public ApiResponse<List<LeaveGrantRuleResponse>> listGrantRules(
            @RequestParam(required = false) String calendarCode
    ) {
        return ApiResponse.ok(leaveTypeService.listGrantRules(calendarCode));
    }

    @Operation(summary = "Create leave grant rule")
    @PostMapping("/leave-grant-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<LeaveGrantRuleResponse> createGrantRule(
            @Valid @RequestBody LeaveGrantRuleUpsertRequest req
    ) {
        return ApiResponse.ok(leaveTypeService.createGrantRule(req));
    }

    @Operation(summary = "Update leave grant rule")
    @PutMapping("/leave-grant-rules/{id}")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<LeaveGrantRuleResponse> updateGrantRule(
            @PathVariable Long id,
            @Valid @RequestBody LeaveGrantRuleUpsertRequest req
    ) {
        return ApiResponse.ok(leaveTypeService.updateGrantRule(id, req));
    }

    @Operation(summary = "Trigger annual leave seniority grant batch for all eligible employees")
    @PostMapping("/leave-balances/grant-annual")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<AnnualLeaveGrantBatchResultDto> grantAnnualLeave(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        LocalDate targetDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ApiResponse.ok(annualLeaveGrantService.grantAnnualLeaveForAllUsers(targetDate));
    }

    @Operation(summary = "Manual HR grant of leave balance bucket")
    @PostMapping("/leave-balances/manual-grant")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<LeaveBalanceBucketDto> manualGrant(
            @Valid @RequestBody ManualLeaveGrantRequest req
    ) {
        return ApiResponse.ok(attendanceRequestService.manualGrant(req));
    }

    @Operation(summary = "Get leave balance summary dashboard, 90-day expiring comp-leave, and JP 5-day obligation")
    @GetMapping("/leave-dashboard")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self', 'att:record:read')")
    public ApiResponse<LeaveDashboardResponse> getLeaveDashboard(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        var principal = SecurityUtils.requirePrincipal();
        Long targetUserId = userId != null ? userId : principal.getUserId();
        if (!targetUserId.equals(principal.getUserId())
                && principal.getDataScope() == io.github.skr416.atria.iam.model.DataScopeType.SELF_ONLY
                && !principal.getPermissionCodes().contains("att:rule:write")) {
            throw new io.github.skr416.atria.common.exception.BusinessException(io.github.skr416.atria.common.api.ErrorCode.FORBIDDEN);
        }
        LocalDate targetDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ApiResponse.ok(leaveDashboardQuery.getDashboard(targetUserId, targetDate));
    }

    @Operation(summary = "List regional overtime rate rules and multipliers")
    @GetMapping("/overtime-rate-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:read', 'att:calendar:read', 'att:request:self', 'att:punch:self')")
    public ApiResponse<List<OvertimeRateRuleResponse>> listOvertimeRateRules(
            @RequestParam(required = false) String calendarCode
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.listRateRules(calendarCode));
    }

    @Operation(summary = "Create overtime rate rule")
    @PostMapping("/overtime-rate-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<OvertimeRateRuleResponse> createOvertimeRateRule(
            @Valid @RequestBody OvertimeRateRuleUpsertRequest req
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.createRateRule(req));
    }

    @Operation(summary = "Update overtime rate rule")
    @PutMapping("/overtime-rate-rules/{id}")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<OvertimeRateRuleResponse> updateOvertimeRateRule(
            @PathVariable Long id,
            @Valid @RequestBody OvertimeRateRuleUpsertRequest req
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.updateRateRule(id, req));
    }

    @Operation(summary = "List regional overtime cap rules")
    @GetMapping("/overtime-cap-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:read', 'att:calendar:read', 'att:request:self', 'att:punch:self')")
    public ApiResponse<List<OvertimeCapRuleResponse>> listOvertimeCapRules(
            @RequestParam(required = false) String calendarCode
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.listCapRules(calendarCode));
    }

    @Operation(summary = "Create overtime cap rule")
    @PostMapping("/overtime-cap-rules")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<OvertimeCapRuleResponse> createOvertimeCapRule(
            @Valid @RequestBody OvertimeCapRuleUpsertRequest req
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.createCapRule(req));
    }

    @Operation(summary = "Update overtime cap rule")
    @PutMapping("/overtime-cap-rules/{id}")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write')")
    public ApiResponse<OvertimeCapRuleResponse> updateOvertimeCapRule(
            @PathVariable Long id,
            @Valid @RequestBody OvertimeCapRuleUpsertRequest req
    ) {
        return ApiResponse.ok(overtimeRuleAndCalculatorService.updateCapRule(id, req));
    }

    @Operation(summary = "List compensatory leave grants with live bucket balance figures")
    @GetMapping("/comp-leave-grants")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self', 'att:record:read')")
    public ApiResponse<List<CompLeaveGrantResponse>> listCompLeaveGrants(
            @RequestParam(required = false) Long userId
    ) {
        var principal = SecurityUtils.requirePrincipal();
        Long targetUserId = userId != null ? userId : principal.getUserId();
        if (!targetUserId.equals(principal.getUserId())
                && principal.getDataScope() == io.github.skr416.atria.iam.model.DataScopeType.SELF_ONLY
                && !principal.getPermissionCodes().contains("att:rule:write")) {
            throw new io.github.skr416.atria.common.exception.BusinessException(io.github.skr416.atria.common.api.ErrorCode.FORBIDDEN);
        }
        return ApiResponse.ok(compLeaveGrantService.listGrants(targetUserId));
    }

    @Operation(summary = "Settle expired compensatory leave grant to overtime cash payout (Taiwan LSA Art. 32-1)")
    @PostMapping("/comp-leave-grants/{id}/settle-expired")
    @PreAuthorize("hasAnyAuthority('att:rule:write', 'att:calendar:write', 'att:monthly-close:manage')")
    public ApiResponse<CompLeaveExpirySettlementResultDto> settleExpiredGrant(
            @PathVariable Long id
    ) {
        return ApiResponse.ok(compLeaveGrantService.settleExpiredGrant(id));
    }

    @Operation(summary = "List current employee's submitted attendance requests")
    @GetMapping("/requests/my")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self')")
    public ApiResponse<List<AttendanceRequestResponse>> listMyRequests() {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(attendanceRequestService.listMyRequests(userId));
    }

    @Operation(summary = "List all attendance requests (filtered by status)")
    @GetMapping("/requests")
    @PreAuthorize("hasAnyAuthority('att:rule:read', 'att:calendar:read', 'att:record:read')")
    public ApiResponse<List<AttendanceRequestResponse>> listRequests(
            @RequestParam(required = false) AttendanceRequestStatus status
    ) {
        return ApiResponse.ok(attendanceRequestService.listAllRequests(status));
    }

    @Operation(summary = "Submit a leave request")
    @PostMapping("/requests/leave")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self')")
    public ApiResponse<AttendanceRequestResponse> submitLeaveRequest(
            @Valid @RequestBody LeaveRequestSubmitDto dto
    ) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(attendanceRequestService.submitLeaveRequest(userId, dto));
    }

    @Operation(summary = "Submit an overtime request")
    @PostMapping("/requests/overtime")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self')")
    public ApiResponse<AttendanceRequestResponse> submitOvertimeRequest(
            @Valid @RequestBody OvertimeRequestSubmitDto dto
    ) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(attendanceRequestService.submitOvertimeRequest(userId, dto));
    }

    @Operation(summary = "Submit a punch correction request")
    @PostMapping("/requests/punch-correction")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self')")
    public ApiResponse<AttendanceRequestResponse> submitPunchCorrectionRequest(
            @Valid @RequestBody PunchCorrectionRequestSubmitDto dto
    ) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(attendanceRequestService.submitPunchCorrectionRequest(userId, dto));
    }

    @Operation(summary = "Withdraw / cancel a pending attendance request")
    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('att:request:self', 'att:punch:self')")
    public ApiResponse<AttendanceRequestResponse> cancelRequest(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(attendanceRequestService.cancelRequest(id, userId));
    }
}
