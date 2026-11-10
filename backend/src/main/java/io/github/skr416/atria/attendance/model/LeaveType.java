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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Leave type definition in {@code att_leave_type} with trilingual name and minimum unit rounding
 * (ADR-0008 Decision 4).
 */
@Getter
@Setter
@Entity
@Table(name = "att_leave_type")
@SQLRestriction("is_deleted = false")
public class LeaveType extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Column(name = "min_unit_minutes", nullable = false)
    private int minUnitMinutes = 60;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "balance_tracked", nullable = false)
    private boolean balanceTracked = true;

    @Column(name = "attachment_required", nullable = false)
    private boolean attachmentRequired = false;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "description", length = 255)
    private String description;
}
