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
import org.hibernate.annotations.SQLRestriction;

/**
 * Structured workflow activation condition in {@code wf_flow_node_condition} (ADR-0008 Decision 10).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_flow_node_condition")
@SQLRestriction("is_deleted = false")
public class FlowNodeCondition extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_node_id", nullable = false)
    private Long flowNodeId;

    @Column(name = "logical_group", nullable = false)
    private int logicalGroup = 1;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 16)
    private ConditionOperator operator;

    @Column(name = "expected_value", nullable = false, length = 255)
    private String expectedValue;
}
