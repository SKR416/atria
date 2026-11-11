package io.github.skr416.atria.workflow.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WorkflowDeputyCreateRequest(
        Long delegatorUserId,
        @NotNull Long deputyUserId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String reason
) {
}
