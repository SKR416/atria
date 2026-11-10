package io.github.skr416.atria.attendance.model;

/**
 * Compensatory leave conversion mode in {@code att_overtime_rate_rule} and {@code att_comp_leave_grant}
 * (ADR-0008 Decision 6).
 */
public enum CompLeaveConversionMode {
    HOURS_1_TO_1,
    RATE_MULTIPLIED
}
