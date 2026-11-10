package io.github.skr416.atria.attendance.model;

/**
 * Enforcement action when an overtime cap rule is exceeded in {@code att_overtime_cap_rule} (ADR-0008 Decision 5).
 */
public enum OvertimeCapAction {
    WARN,
    BLOCK
}
