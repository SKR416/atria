package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.LeaveBalance;

import java.time.LocalDate;

/**
 * FIFO Leave Balance Ledger contract managing per-bucket freeze, commit, and 100% exact restoration
 * via {@code att_leave_balance_tx} audit records (ADR-0008 Decision 4).
 */
public interface LeaveBalanceLedger {

    /**
     * Freezes {@code minutes} across the employee's active buckets ({@code validFrom <= referenceDate <= validUntil})
     * in FIFO {@code valid_until ASC, id ASC} order and writes {@code FREEZE} rows to {@code att_leave_balance_tx}.
     * No-op if the leave type has {@code balanceTracked == false}.
     * Throws {@code ATT_INSUFFICIENT_LEAVE_BALANCE} (2004) if total available balance is insufficient.
     */
    void freeze(Long requestId, Long userId, String leaveTypeCode, int minutes, LocalDate referenceDate);

    /**
     * Commits frozen minutes for {@code requestId} by moving exact per-bucket amounts from
     * {@code pending_minutes} to {@code used_minutes} based on {@code FREEZE} rows in {@code att_leave_balance_tx},
     * and appends {@code COMMIT} rows.
     */
    void commit(Long requestId);

    /**
     * Releases frozen minutes for {@code requestId} (on {@code REJECTED} or {@code CANCELLED})
     * by restoring exact per-bucket {@code pending_minutes} from {@code FREEZE} rows in {@code att_leave_balance_tx},
     * and appends {@code RESTORE_PENDING} rows.
     */
    void restoreFromPending(Long requestId);

    /**
     * Restores committed minutes for {@code requestId} (on post-approval {@code REVOKED})
     * by decrementing exact per-bucket {@code used_minutes} from {@code COMMIT} rows in {@code att_leave_balance_tx},
     * and appends {@code RESTORE_USED} rows.
     */
    void restoreFromUsed(Long requestId);

    /**
     * Creates a new leave balance bucket (for annual leave grants, manual HR grants, or compensatory leave grants).
     */
    LeaveBalance createBucket(
            Long userId,
            String leaveTypeCode,
            String calendarCode,
            int grantYear,
            int totalMinutes,
            LocalDate validFrom,
            LocalDate validUntil,
            Long sourceCompGrantId,
            int mandatoryTargetMinutes,
            String note
    );

    /**
     * Soft-deletes the leave balance bucket linked to a compensatory leave grant upon overtime revocation.
     * Throws {@code ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE} (2025) if any minutes are used or pending.
     */
    void revokeCompGrantBucket(Long sourceCompGrantId);
}
