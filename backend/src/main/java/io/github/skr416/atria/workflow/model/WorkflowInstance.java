package io.github.skr416.atria.workflow.model;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Workflow instance entity in {@code wf_instance} (ADR-0005 Decision 3).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_instance")
@SQLRestriction("is_deleted = false")
public class WorkflowInstance extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_no", nullable = false, length = 48, unique = true)
    private String instanceNo;

    @Column(name = "flow_definition_id", nullable = false)
    private Long flowDefinitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private WorkflowTargetType targetType;

    @Column(name = "att_request_id", unique = true)
    private Long attRequestId;

    @Column(name = "form_template_code", length = 48)
    private String formTemplateCode;

    @Column(name = "form_version")
    private Integer formVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_snapshot_json")
    private List<Map<String, Object>> schemaSnapshotJson;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WorkflowInstanceStatus status = WorkflowInstanceStatus.PENDING;

    @Column(name = "current_step_order")
    private Integer currentStepOrder;

    @Column(name = "current_approver_id")
    private Long currentApproverId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data_json", nullable = false)
    private Map<String, Object> formDataJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_form_data_json")
    private Map<String, Object> previousFormDataJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json")
    private List<Map<String, Object>> diffJson;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount = 1;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
