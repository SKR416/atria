package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.ConditionOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FlowNodeConditionUpsertRequest(
        int logicalGroup,
        @NotBlank String fieldKey,
        @NotNull ConditionOperator operator,
        @NotBlank String expectedValue
) {
}
