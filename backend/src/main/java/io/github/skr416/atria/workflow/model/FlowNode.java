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

import java.util.Map;

/**
 * Ordered workflow approval node in {@code wf_flow_node} (ADR-0005 Decision 2).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_flow_node")
@SQLRestriction("is_deleted = false")
public class FlowNode extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_definition_id", nullable = false)
    private Long flowDefinitionId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "node_code", nullable = false, length = 48)
    private String nodeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false)
    private Map<String, String> name;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_strategy", nullable = false, length = 32)
    private ApproverStrategy approverStrategy;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "self_approval_auto_skip", nullable = false)
    private boolean selfApprovalAutoSkip = true;

    @Column(name = "allow_deputy", nullable = false)
    private boolean allowDeputy = true;
}
