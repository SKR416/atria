package io.github.skr416.atria.attendance.service;

import java.time.LocalDate;

/**
 * Strategy interface for resolving an employee's expected work schedule on a logical work date
 * (ADR-0003 Decision 1).
 */
public interface WorkScheduleResolver {

    /**
     * Resolves the work schedule for a user and logical work date.
     */
    ResolvedWorkSchedule resolve(Long userId, LocalDate workDate);

    /**
     * Resolves the calendar schedule directly by calendar code and timezone.
     */
    ResolvedWorkSchedule resolveForCalendar(String calendarCode, String timezone, LocalDate workDate);
}
