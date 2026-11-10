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

import java.util.Map;

/**
 * Regional overtime cap rule in {@code att_overtime_cap_rule} with {@code WARN} or {@code BLOCK}
 * enforcement action (ADR-0008 Decision 5).
 */
@Getter
@Setter
@Entity
@Table(name = "att_overtime_cap_rule")
@SQLRestriction("is_deleted = false")
public class OvertimeCapRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_code", nullable = false, length = 16)
    private String calendarCode;

    @Column(name = "rule_code", nullable = false, length = 48, unique = true)
    private String ruleCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Column(name = "period_months", nullable = false)
    private int periodMonths = 1;

    @Column(name = "max_minutes", nullable = false)
    private int maxMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "enforcement_action", nullable = false, length = 16)
    private OvertimeCapAction enforcementAction;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 255)
    private String description;
}
