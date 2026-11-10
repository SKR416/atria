package io.github.skr416.atria.attendance.dto.leave;

import java.time.LocalDate;
import java.util.Map;

/**
 * Result of evaluating a leave time interval against a user's work calendar, lunch breaks,
 * and minimum leave unit rounding (ADR-0008 Decision 4).
 */
public record LeaveDurationResult(
        LocalDate startDate,
        LocalDate endDate,
        int rawOverlapMinutes,
        int roundedNetMinutes,
        int minUnitMinutes,
        Map<LocalDate, Integer> dailyMinutesMap
) {
}
