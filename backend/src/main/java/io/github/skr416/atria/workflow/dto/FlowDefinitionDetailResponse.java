package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.WorkflowTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FlowDefinitionDetailResponse(
        Long id,
        String code,
        Map<String, String> name,
        WorkflowTargetType targetType,
        String formTemplateCode,
        boolean enabled,
        String description,
        List<FlowNodeResponse> nodes,
        Instant createdAt,
        Instant updatedAt
) {
}
