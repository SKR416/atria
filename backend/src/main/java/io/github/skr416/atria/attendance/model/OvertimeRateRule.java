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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Regional overtime rate rule in {@code att_overtime_rate_rule} defining cumulative-minute bands
 * and multipliers (e.g. TW 1.34 / 1.67 / 2.67 / 2.00; JP 1.25 / 1.35) (ADR-0008 Decision 5).
 */
@Getter
@Setter
@Entity
@Table(name = "att_overtime_rate_rule")
@SQLRestriction("is_deleted = false")
public class OvertimeRateRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    private CalendarDayType dayType;

    @Column(name = "from_minute", nullable = false)
    private int fromMinute;

    @Column(name = "to_minute", nullable = false)
    private int toMinute;

    @Column(name = "multiplier", nullable = false, precision = 6, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "is_night_premium", nullable = false)
    private boolean nightPremium = false;

    @Column(name = "is_monthly_cumulative", nullable = false)
    private boolean monthlyCumulative = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_comp_leave_mode", nullable = false, length = 24)
    private CompLeaveConversionMode defaultCompLeaveMode = CompLeaveConversionMode.RATE_MULTIPLIED;

    @Column(name = "description", length = 255)
    private String description;
}
