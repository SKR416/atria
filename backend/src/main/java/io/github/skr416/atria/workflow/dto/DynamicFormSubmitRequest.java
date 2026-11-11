package io.github.skr416.atria.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record DynamicFormSubmitRequest(
        Long instanceId,
        @NotBlank String formTemplateCode,
        @NotBlank String title,
        @NotNull Map<String, Object> formData
) {
}
