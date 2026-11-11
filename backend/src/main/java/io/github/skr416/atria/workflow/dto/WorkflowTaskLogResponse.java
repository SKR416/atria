package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.WorkflowTaskAction;

import java.time.Instant;
import java.util.Map;

public record WorkflowTaskLogResponse(
        Long id,
        Long instanceId,
        int stepOrder,
        Map<String, String> nodeName,
        WorkflowTaskAction action,
        Long assignedApproverId,
        String assignedApproverName,
        Long actorUserId,
        String actorUserName,
        Long delegatorUserId,
        String delegatorUserName,
        Long reassignedToUserId,
        String reassignedToUserName,
        String comment,
        Instant actedAt
) {
}
