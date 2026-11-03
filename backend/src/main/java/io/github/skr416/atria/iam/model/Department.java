package io.github.skr416.atria.iam.model;

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
 * Department tree node. {@code treePath} is a materialized path such as {@code /1/4/12/} (ADR-0004).
 */
@Getter
@Setter
@Entity
@Table(name = "iam_department")
@SQLRestriction("is_deleted = false")
public class Department extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "tree_path", nullable = false, length = 512)
    private String treePath;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Column(name = "leader_id")
    private Long leaderId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Taipei";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
