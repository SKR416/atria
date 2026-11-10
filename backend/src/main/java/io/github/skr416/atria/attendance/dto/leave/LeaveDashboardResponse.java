package io.github.skr416.atria.attendance.dto.leave;

import java.time.LocalDate;
import java.util.List;

public record LeaveDashboardResponse(
        Long userId,
        String calendarCode,
        LocalDate asOfDate,
        List<LeaveTypeBalanceSummaryDto> balances,
        List<ExpiringCompLeaveDto> expiringCompLeavesWithin90Days,
        JpFiveDayObligationDto jpFiveDayObligation
) {
}
