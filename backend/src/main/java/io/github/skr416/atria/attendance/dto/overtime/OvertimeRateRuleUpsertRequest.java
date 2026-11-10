package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OvertimeRateRuleUpsertRequest(
        @NotBlank String calendarCode,
        @NotNull CalendarDayType dayType,
        @Min(0) int fromMinute,
        @Min(1) int toMinute,
        @NotNull @DecimalMin("0.01") BigDecimal multiplier,
        boolean nightPremium,
        boolean monthlyCumulative,
        @NotNull CompLeaveConversionMode defaultCompLeaveMode,
        String description
) {
}
