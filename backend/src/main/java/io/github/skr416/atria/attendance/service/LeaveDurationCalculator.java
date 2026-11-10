package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.LeaveDurationResult;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Contract for calculating net leave duration by intersecting a requested UTC interval with
 * the employee's regional work calendar, deducting the daily break window (12:00–13:00),
 * and rounding UP (ceiling) to the leave type's {@code min_unit_minutes} (ADR-0008 Decision 4).
 */
public interface LeaveDurationCalculator {

    /**
     * Evaluates a leave interval across one or more calendar dates and returns the rounded net duration.
     * Throws {@code ATT_INVALID_LEAVE_RANGE} if {@code !endTime.isAfter(startTime)} or
     * {@code ATT_LEAVE_ZERO_DURATION} if net overlap with working hours is 0.
     */
    LeaveDurationResult calculateDuration(Long userId, String leaveTypeCode, Instant startTime, Instant endTime);

    /**
     * Calculates net leave minutes on a single logical work date (without cross-day aggregation),
     * used by {@link AttendanceRecordCalculator} when aggregating approved daily leave minutes.
     */
    int calculateSingleDayNetLeaveMinutes(Long userId, LocalDate workDate, Instant leaveStart, Instant leaveEnd);
}
