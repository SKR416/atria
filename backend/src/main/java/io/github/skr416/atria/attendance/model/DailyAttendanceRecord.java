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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Derived daily attendance record per user and logical work date {@code att_daily_record}
 * (ADR-0008 Decision 1).
 */
@Getter
@Setter
@Entity
@Table(name = "att_daily_record")
@SQLRestriction("is_deleted = false")
public class DailyAttendanceRecord extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    private CalendarDayType dayType;

    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    @Column(name = "first_raw_clock_in")
    private Instant firstRawClockIn;

    @Column(name = "last_raw_clock_out")
    private Instant lastRawClockOut;

    @Column(name = "effective_clock_in")
    private Instant effectiveClockIn;

    @Column(name = "effective_clock_out")
    private Instant effectiveClockOut;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes = 0;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes = 0;

    @Column(name = "early_leave_minutes", nullable = false)
    private int earlyLeaveMinutes = 0;

    @Column(name = "leave_minutes", nullable = false)
    private int leaveMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AttendanceStatus status;

    @Column(name = "punch_count", nullable = false)
    private int punchCount = 0;

    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    @Column(name = "note", length = 500)
    private String note;
}
