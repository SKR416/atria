package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import io.github.skr416.atria.attendance.model.OvertimeRateRule;

import java.math.BigDecimal;

public record OvertimeRateRuleResponse(
        Long id,
        String calendarCode,
        CalendarDayType dayType,
        int fromMinute,
        int toMinute,
        BigDecimal multiplier,
        boolean nightPremium,
        boolean monthlyCumulative,
        CompLeaveConversionMode defaultCompLeaveMode,
        String description
) {
    public static OvertimeRateRuleResponse from(OvertimeRateRule r) {
        return new OvertimeRateRuleResponse(
                r.getId(),
                r.getCalendarCode(),
                r.getDayType(),
                r.getFromMinute(),
                r.getToMinute(),
                r.getMultiplier(),
                r.isNightPremium(),
                r.isMonthlyCumulative(),
                r.getDefaultCompLeaveMode(),
                r.getDescription()
        );
    }
}
