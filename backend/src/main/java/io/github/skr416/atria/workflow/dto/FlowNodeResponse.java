package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.ApproverStrategy;

import java.util.List;
import java.util.Map;

public record FlowNodeResponse(
        Long id,
        Long flowDefinitionId,
        int stepOrder,
        String nodeCode,
        Map<String, String> name,
        ApproverStrategy approverStrategy,
        String roleCode,
        boolean selfApprovalAutoSkip,
        boolean allowDeputy,
        List<FlowNodeConditionResponse> conditions
) {
}
