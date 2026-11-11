package io.github.skr416.atria.workflow.model;

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

import java.util.List;
import java.util.Map;

/**
 * Dynamic form template entity in {@code wf_form_template} with JSONB schema and versioning (ADR-0005 Decision 1).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_form_template")
@SQLRestriction("is_deleted = false")
public class FormTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 48, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Column(name = "category", nullable = false, length = 24)
    private String category = "GENERAL";

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false)
    private List<Map<String, Object>> schemaJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 255)
    private String description;
}
