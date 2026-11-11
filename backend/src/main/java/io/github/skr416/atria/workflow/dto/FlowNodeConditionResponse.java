package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.ConditionOperator;

public record FlowNodeConditionResponse(
        Long id,
        Long flowNodeId,
        int logicalGroup,
        String fieldKey,
        ConditionOperator operator,
        String expectedValue
) {
}
