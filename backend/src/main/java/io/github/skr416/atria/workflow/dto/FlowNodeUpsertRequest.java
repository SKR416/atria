package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.ApproverStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record FlowNodeUpsertRequest(
        int stepOrder,
        @NotBlank String nodeCode,
        @NotEmpty Map<String, String> name,
        @NotNull ApproverStrategy approverStrategy,
        String roleCode,
        Boolean selfApprovalAutoSkip,
        Boolean allowDeputy,
        List<FlowNodeConditionUpsertRequest> conditions
) {
}
