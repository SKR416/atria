package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.CalendarDayType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Resolved expected work schedule and timezone context for an employee on a given date (ADR-0003).
 */
public record ResolvedWorkSchedule(
        Long userId,
        Long departmentId,
        String calendarCode,
        ZoneId timezone,
        LocalDate workDate,
        CalendarDayType dayType,
        LocalTime workStartTime,
        LocalTime workEndTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        Map<String, String> holidayName
) {
    public boolean isWorkingDay() {
        return dayType != null && dayType.isWorkingDay();
    }
}
