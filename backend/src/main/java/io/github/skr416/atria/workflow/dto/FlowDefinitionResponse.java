package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.WorkflowTargetType;

import java.time.Instant;
import java.util.Map;

public record FlowDefinitionResponse(
        Long id,
        String code,
        Map<String, String> name,
        WorkflowTargetType targetType,
        String formTemplateCode,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
