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

/**
 * Regional punch rounding, grace period, and cross-midnight cutoff rule {@code att_punch_rule}
 * (ADR-0003 & ADR-0008).
 */
@Getter
@Setter
@Entity
@Table(name = "att_punch_rule")
@SQLRestriction("is_deleted = false")
public class PunchRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16, unique = true)
    private String calendarCode;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes = 5;

    @Column(name = "early_leave_grace_minutes", nullable = false)
    private int earlyLeaveGraceMinutes = 0;

    @Column(name = "clock_in_rounding_minutes", nullable = false)
    private int clockInRoundingMinutes = 1;

    @Column(name = "clock_out_rounding_minutes", nullable = false)
    private int clockOutRoundingMinutes = 1;

    @Column(name = "day_cutoff_hour", nullable = false)
    private int dayCutoffHour = 4;

    @Column(name = "min_punch_interval_seconds", nullable = false)
    private int minPunchIntervalSeconds = 60;

    @Column(name = "max_retroactive_days", nullable = false)
    private int maxRetroactiveDays = 14;

    @Column(name = "auto_monthly_close_day", nullable = false)
    private int autoMonthlyCloseDay = 5;
}
