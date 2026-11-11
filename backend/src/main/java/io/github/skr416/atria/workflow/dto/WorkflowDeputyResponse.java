package io.github.skr416.atria.workflow.dto;

import java.time.Instant;
import java.time.LocalDate;

public record WorkflowDeputyResponse(
        Long id,
        Long delegatorUserId,
        String delegatorName,
        Long deputyUserId,
        String deputyName,
        LocalDate startDate,
        LocalDate endDate,
        boolean enabled,
        String reason,
        Instant createdAt
) {
}
