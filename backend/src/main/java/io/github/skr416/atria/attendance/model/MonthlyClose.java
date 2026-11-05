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

/**
 * Monthly attendance closing lock per calendar region & {@code YYYY-MM} period
 * {@code att_monthly_close} (ADR-0003 & ADR-0008 Decision 7).
 */
@Getter
@Setter
@Entity
@Table(name = "att_monthly_close")
@SQLRestriction("is_deleted = false")
public class MonthlyClose extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonthlyCloseStatus status = MonthlyCloseStatus.CLOSED;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_by_username", length = 64)
    private String closedByUsername;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopened_by")
    private Long reopenedBy;

    @Column(name = "note", length = 500)
    private String note;
}
