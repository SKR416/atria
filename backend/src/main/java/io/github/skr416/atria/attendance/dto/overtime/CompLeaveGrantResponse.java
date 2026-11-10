package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import io.github.skr416.atria.attendance.model.OvertimeBandBreakdownItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompLeaveGrantResponse(
        Long id,
        Long userId,
        String calendarCode,
        Long sourceRequestId,
        LocalDate workDate,
        CalendarDayType dayType,
        CompLeaveConversionMode compLeaveMode,
        int originalOvertimeMinutes,
        int grantedMinutes,
        int usedMinutes,
        int pendingMinutes,
        int availableMinutes,
        int residualPayableMinutes,
        List<OvertimeBandBreakdownItem> bandBreakdown,
        Instant grantedAt,
        LocalDate expiresAt,
        boolean expiredSettled,
        int expiredPayableWeightedMinutes
) {
}
