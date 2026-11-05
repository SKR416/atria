package io.github.skr416.atria.attendance.dto.calendar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request payload for updating regional punch & cutoff rules {@code att_punch_rule}.
 */
public record PunchRuleUpdateRequest(
        @Min(0) @Max(120)
        int lateGraceMinutes,

        @Min(0) @Max(120)
        int earlyLeaveGraceMinutes,

        @Min(1) @Max(60)
        int clockInRoundingMinutes,

        @Min(1) @Max(60)
        int clockOutRoundingMinutes,

        @Min(0) @Max(12)
        int dayCutoffHour,

        @Min(0) @Max(3600)
        int minPunchIntervalSeconds,

        @Min(1) @Max(90)
        int maxRetroactiveDays,

        @Min(0) @Max(28)
        int autoMonthlyCloseDay
) {
}
