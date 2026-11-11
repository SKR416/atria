package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;

import java.time.Instant;

public record WorkflowInstanceResponse(
        Long id,
        String instanceNo,
        Long flowDefinitionId,
        WorkflowTargetType targetType,
        Long attRequestId,
        String formTemplateCode,
        Integer formVersion,
        Long applicantId,
        String applicantName,
        Long departmentId,
        String departmentName,
        String title,
        WorkflowInstanceStatus status,
        Integer currentStepOrder,
        Long currentApproverId,
        String currentApproverName,
        int submissionCount,
        Instant submittedAt,
        Instant completedAt,
        Instant createdAt
) {
}
