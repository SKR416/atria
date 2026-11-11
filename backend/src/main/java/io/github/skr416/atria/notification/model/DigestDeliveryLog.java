package io.github.skr416.atria.notification.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Map;

/**
 * Idempotent delivery log for monthly leave balance digest notifications (ADR-0008 Decision 8).
 */
@Getter
@Setter
@Entity
@Table(name = "ntf_digest_log")
@SQLRestriction("is_deleted = false")
public class DigestDeliveryLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "period_year_month", nullable = false, length = 7)
    private String periodYearMonth;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "notification_id")
    private Long notificationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false)
    private Map<String, Object> summaryJson;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;
}
