package io.github.skr416.atria.attendance.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * Employee leave balance bucket in {@code att_leave_balance} (single source of truth for annual
 * and compensatory leave balances, deducted in FIFO {@code valid_until ASC} order) (ADR-0008 Decision 4).
 */
@Getter
@Setter
@Entity
@Table(name = "att_leave_balance")
@SQLRestriction("is_deleted = false")
public class LeaveBalance extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_code", nullable = false, length = 32)
    private String leaveTypeCode;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "grant_year", nullable = false)
    private int grantYear;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "used_minutes", nullable = false)
    private int usedMinutes = 0;

    @Column(name = "pending_minutes", nullable = false)
    private int pendingMinutes = 0;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "source_comp_grant_id")
    private Long sourceCompGrantId;

    @Column(name = "mandatory_target_minutes", nullable = false)
    private int mandatoryTargetMinutes = 0;

    @Column(name = "note", length = 255)
    private String note;

    /**
     * Returns available remaining minutes excluding used and frozen pending minutes.
     */
    public int getAvailableMinutes() {
        return Math.max(0, totalMinutes - usedMinutes - pendingMinutes);
    }
}
