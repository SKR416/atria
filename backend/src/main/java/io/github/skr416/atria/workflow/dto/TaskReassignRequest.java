package io.github.skr416.atria.workflow.dto;

import jakarta.validation.constraints.NotNull;

public record TaskReassignRequest(
        @NotNull Long newApproverUserId,
        String comment
) {
}
