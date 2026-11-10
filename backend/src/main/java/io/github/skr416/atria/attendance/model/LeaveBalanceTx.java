package io.github.skr416.atria.attendance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Audit trail entry in {@code att_leave_balance_tx} recording per-bucket FIFO balance movements
 * ({@code FREEZE}, {@code COMMIT}, {@code RESTORE_PENDING}, {@code RESTORE_USED}) so that rejections,
 * cancellations, and revocations restore 100% accurately to the original buckets (ADR-0008 Decision 4).
 */
@Getter
@Setter
@Entity
@Table(name = "att_leave_balance_tx")
public class LeaveBalanceTx {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "balance_id", nullable = false, updatable = false)
    private Long balanceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "leave_type_code", nullable = false, length = 32, updatable = false)
    private String leaveTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 24, updatable = false)
    private LeaveBalanceTxType txType;

    @Column(name = "minutes", nullable = false, updatable = false)
    private int minutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
