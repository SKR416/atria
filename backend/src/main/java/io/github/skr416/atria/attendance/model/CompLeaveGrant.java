package io.github.skr416.atria.attendance.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Compensatory leave grant record in {@code att_comp_leave_grant} storing original rate-band breakdown
 * JSON for accurate expiry payout without double-multiplication drift (ADR-0008 Decision 6).
 * Note: Used and pending minutes are tracked solely in the linked {@link LeaveBalance} bucket.
 */
@Getter
@Setter
@Entity
@Table(name = "att_comp_leave_grant")
@SQLRestriction("is_deleted = false")
public class CompLeaveGrant extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "source_request_id", nullable = false, unique = true)
    private Long sourceRequestId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    private CalendarDayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "comp_leave_mode", nullable = false, length = 24)
    private CompLeaveConversionMode compLeaveMode;

    @Column(name = "original_overtime_minutes", nullable = false)
    private int originalOvertimeMinutes;

    @Column(name = "granted_minutes", nullable = false)
    private int grantedMinutes;

    @Column(name = "residual_payable_minutes", nullable = false)
    private int residualPayableMinutes = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "band_breakdown_json", nullable = false)
    private List<OvertimeBandBreakdownItem> bandBreakdownJson;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(name = "expired_settled", nullable = false)
    private boolean expiredSettled = false;

    @Column(name = "expired_payable_weighted_minutes", nullable = false)
    private int expiredPayableWeightedMinutes = 0;
}
