package io.github.skr416.atria.workflow.dto;

import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkflowInstanceDetailResponse(
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
        Map<String, Object> formDataJson,
        Map<String, Object> previousFormDataJson,
        List<Map<String, Object>> diffJson,
        List<Map<String, Object>> schemaSnapshotJson,
        List<WorkflowTaskLogResponse> taskLogs,
        Instant submittedAt,
        Instant completedAt,
        Instant createdAt
) {
}
