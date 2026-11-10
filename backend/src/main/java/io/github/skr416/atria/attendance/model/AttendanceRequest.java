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

/**
 * Unified attendance request entity in {@code att_request} covering {@code LEAVE}, {@code OVERTIME},
 * and {@code PUNCH_CORRECTION} with optimistic locking and state machine lifecycle (ADR-0008 Decisions 3 & 9).
 */
@Getter
@Setter
@Entity
@Table(name = "att_request")
@SQLRestriction("is_deleted = false")
public class AttendanceRequest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private AttendanceRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceRequestStatus status = AttendanceRequestStatus.PENDING;

    @Column(name = "leave_type_code", length = 32)
    private String leaveTypeCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "requested_minutes", nullable = false)
    private int requestedMinutes = 0;

    @Column(name = "weighted_minutes", nullable = false)
    private int weightedMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "overtime_comp_type", length = 20)
    private OvertimeCompensationType overtimeCompType;

    @Enumerated(EnumType.STRING)
    @Column(name = "comp_leave_mode", length = 24)
    private CompLeaveConversionMode compLeaveMode;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "attachment_path", length = 255)
    private String attachmentPath;

    @Column(name = "warning_messages", length = 500)
    private String warningMessages;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;
}
