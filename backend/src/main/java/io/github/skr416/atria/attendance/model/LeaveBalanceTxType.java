package io.github.skr416.atria.attendance.model;

/**
 * FIFO balance ledger transaction type in {@code att_leave_balance_tx} (ADR-0008 Decision 4).
 */
public enum LeaveBalanceTxType {
    FREEZE,
    COMMIT,
    RESTORE_PENDING,
    RESTORE_USED
}
