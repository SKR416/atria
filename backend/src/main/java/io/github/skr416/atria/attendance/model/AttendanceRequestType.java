package io.github.skr416.atria.attendance.model;

/**
 * Type of employee attendance request in {@code att_request} (ADR-0008 Decisions 3 & 9).
 */
public enum AttendanceRequestType {
    LEAVE,
    OVERTIME,
    PUNCH_CORRECTION
}
