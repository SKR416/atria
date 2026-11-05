package io.github.skr416.atria.attendance.model;

/**
 * Evaluated daily attendance status stored in {@code att_daily_record} (ADR-0003 & ADR-0008 Decision 1).
 */
public enum AttendanceStatus {
    /** Normal attendance meeting scheduled start/end times */
    NORMAL,
    /** Clocked in past scheduled start time + grace period */
    LATE,
    /** Clocked out prior to scheduled end time - grace period */
    EARLY_LEAVE,
    /** Only one punch recorded or incomplete pair on a workday */
    MISSING_PUNCH,
    /** Covered by an approved leave request */
    ON_LEAVE,
    /** No punches and no leave on a scheduled workday after daily cutoff */
    ABSENT,
    /** Non-working day (rest day / statutory rest day / national holiday) */
    HOLIDAY
}
