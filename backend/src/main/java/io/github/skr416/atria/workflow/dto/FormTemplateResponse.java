package io.github.skr416.atria.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FormTemplateResponse(
        Long id,
        String code,
        Map<String, String> name,
        String category,
        int currentVersion,
        List<Map<String, Object>> schemaJson,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
