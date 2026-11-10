package io.github.skr416.atria.attendance.model;

/**
 * Lifecycle state machine status for {@code att_request} (ADR-0008 Decision 9).
 * <ul>
 *   <li>{@code PENDING} -> {@code APPROVED} | {@code REJECTED}</li>
 *   <li>{@code PENDING} -> {@code CANCELLED} (withdrawn by applicant)</li>
 *   <li>{@code APPROVED} -> {@code REVOKED} (post-approval revocation)</li>
 * </ul>
 */
public enum AttendanceRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REVOKED
}
