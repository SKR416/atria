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

/**
 * Seniority-based annual leave grant tier rule in {@code att_leave_grant_rule}
 * for Taiwan (Art. 38) and Japan (Art. 39) statutory templates (ADR-0008 Decision 4).
 */
@Getter
@Setter
@Entity
@Table(name = "att_leave_grant_rule")
@SQLRestriction("is_deleted = false")
public class LeaveGrantRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "leave_type_code", nullable = false, length = 32)
    private String leaveTypeCode = "ANNUAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_basis", nullable = false, length = 20)
    private LeaveGrantBasis grantBasis = LeaveGrantBasis.ANNIVERSARY;

    @Column(name = "min_tenure_months", nullable = false)
    private int minTenureMonths;

    @Column(name = "max_tenure_months")
    private Integer maxTenureMonths;

    @Column(name = "grant_days", nullable = false)
    private int grantDays;

    @Column(name = "grant_minutes", nullable = false)
    private int grantMinutes;

    @Column(name = "valid_months", nullable = false)
    private int validMonths = 12;

    @Column(name = "note", length = 255)
    private String note;
}
