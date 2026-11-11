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
import java.util.Map;

/**
 * Immutable workflow task audit trail in {@code wf_task_log} (ADR-0005 Decision 3).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_task_log")
@SQLRestriction("is_deleted = false")
public class WorkflowTaskLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "node_name", nullable = false)
    private Map<String, String> nodeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private WorkflowTaskAction action;

    @Column(name = "assigned_approver_id")
    private Long assignedApproverId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "delegator_user_id")
    private Long delegatorUserId;

    @Column(name = "reassigned_to_user_id")
    private Long reassignedToUserId;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;
}
