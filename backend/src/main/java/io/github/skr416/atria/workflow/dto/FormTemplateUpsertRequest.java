package io.github.skr416.atria.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record FormTemplateUpsertRequest(
        @NotBlank String code,
        @NotEmpty Map<String, String> name,
        String category,
        @NotNull List<Map<String, Object>> schemaJson,
        Boolean enabled,
        String description
) {
}
