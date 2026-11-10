package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import io.github.skr416.atria.attendance.model.CompLeaveGrant;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.model.OvertimeCompensationType;
import io.github.skr416.atria.attendance.model.PunchRule;
import io.github.skr416.atria.attendance.repository.CompLeaveGrantRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Guard service enforcing monthly close locks, retroactive application limits, and compensatory-leave
 * revocation constraints at request submission and revocation entry points (ADR-0008 Decisions 7 & 9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttRequestGuardImpl implements AttRequestGuard {

    private final MonthlyCloseService monthlyCloseService;
    private final AttendanceRecordCalculator attendanceRecordCalculator;
    private final CompLeaveGrantRepository compLeaveGrantRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Override
    public void assertMonthOpen(String calendarCode, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return;
        }
        LocalDate from = startDate.isBefore(endDate) ? startDate : endDate;
        LocalDate to = startDate.isBefore(endDate) ? endDate : startDate;
        YearMonth startYm = YearMonth.from(from);
        YearMonth endYm = YearMonth.from(to);

        for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
            if (monthlyCloseService.isMonthClosed(calendarCode, ym)) {
                throw new BusinessException(ErrorCode.ATT_MONTHLY_PERIOD_CLOSED);
            }
        }
    }

    @Override
    public void assertRetroactiveLimit(String calendarCode, LocalDate targetDate, LocalDate today) {
        if (targetDate == null) {
            return;
        }
        LocalDate asOf = (today != null) ? today : LocalDate.now();
        PunchRule rule = attendanceRecordCalculator.resolvePunchRule(calendarCode);
        int maxRetroDays = (rule != null) ? rule.getMaxRetroactiveDays() : 14;

        if (maxRetroDays > 0 && targetDate.isBefore(asOf.minusDays(maxRetroDays))) {
            throw new BusinessException(ErrorCode.ATT_RETROACTIVE_LIMIT_EXCEEDED);
        }
    }

    @Override
    public void assertCanRevokeApprovedRequest(AttendanceRequest request) {
        if (request == null) {
            return;
        }

        // 1. Touched months must not be closed (ATT_MONTHLY_PERIOD_CLOSED = 2005)
        assertMonthOpen(request.getCalendarCode(), request.getStartDate(), request.getEndDate());

        // 2. If the request is an OVERTIME request with COMP_LEAVE, its granted comp-leave bucket
        //    must have usedMinutes == 0 && pendingMinutes == 0 (ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE = 2025).
        if (request.getRequestType() == AttendanceRequestType.OVERTIME
                && request.getOvertimeCompType() == OvertimeCompensationType.COMP_LEAVE) {
            CompLeaveGrant grant = compLeaveGrantRepository.findBySourceRequestId(request.getId())
                    .orElse(null);
            if (grant != null) {
                LeaveBalance bucket = leaveBalanceRepository.findBySourceCompGrantId(grant.getId())
                        .orElse(null);
                if (bucket != null) {
                    if (bucket.getUsedMinutes() > 0 || bucket.getPendingMinutes() > 0) {
                        throw new BusinessException(ErrorCode.ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE);
                    }
                }
            }
        }
    }
}
