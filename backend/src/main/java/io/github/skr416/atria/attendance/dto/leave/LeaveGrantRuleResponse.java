package io.github.skr416.atria.attendance.dto.leave;

import io.github.skr416.atria.attendance.model.LeaveGrantBasis;
import io.github.skr416.atria.attendance.model.LeaveGrantRule;

public record LeaveGrantRuleResponse(
        Long id,
        String calendarCode,
        String leaveTypeCode,
        LeaveGrantBasis grantBasis,
        int minTenureMonths,
        Integer maxTenureMonths,
        int grantDays,
        int grantMinutes,
        int validMonths,
        String note
) {
    public static LeaveGrantRuleResponse from(LeaveGrantRule r) {
        return new LeaveGrantRuleResponse(
                r.getId(),
                r.getCalendarCode(),
                r.getLeaveTypeCode(),
                r.getGrantBasis(),
                r.getMinTenureMonths(),
                r.getMaxTenureMonths(),
                r.getGrantDays(),
                r.getGrantMinutes(),
                r.getValidMonths(),
                r.getNote()
        );
    }
}
