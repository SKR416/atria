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
import java.time.LocalDate;

/**
 * Append-only immutable raw punch event in {@code att_punch_log} (ADR-0008 Decision 1).
 * Rows in this table must never be updated or deleted to preserve labor-inspection audit trails.
 */
@Getter
@Setter
@Entity
@Table(name = "att_punch_log")
public class PunchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "department_id", updatable = false)
    private Long departmentId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "raw_punch_time", nullable = false, updatable = false)
    private Instant rawPunchTime;

    @Column(name = "client_ip", nullable = false, length = 64, updatable = false)
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20, updatable = false)
    private PunchSource source;

    @Column(name = "bot_client_id", length = 64, updatable = false)
    private String botClientId;

    @Column(name = "correction_request_id", updatable = false)
    private Long correctionRequestId;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
