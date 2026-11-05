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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

/**
 * Multi-region work calendar day entry (e.g. TW / JP) with legal day types and standard shift hours
 * (ADR-0003 & ADR-0008 Decision 2).
 */
@Getter
@Setter
@Entity
@Table(name = "att_work_calendar")
@SQLRestriction("is_deleted = false")
public class WorkCalendarDay extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    private CalendarDayType dayType;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime = LocalTime.of(9, 0);

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime = LocalTime.of(18, 0);

    @Column(name = "break_start_time", nullable = false)
    private LocalTime breakStartTime = LocalTime.of(12, 0);

    @Column(name = "break_end_time", nullable = false)
    private LocalTime breakEndTime = LocalTime.of(13, 0);

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "holiday_name")
    private Map<String, String> holidayName;

    @Column(name = "note", length = 255)
    private String note;
}
