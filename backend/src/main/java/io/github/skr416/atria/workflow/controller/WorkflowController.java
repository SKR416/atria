package io.github.skr416.atria.workflow.controller;

import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.SecurityUtils;
import io.github.skr416.atria.workflow.dto.DynamicFormSubmitRequest;
import io.github.skr416.atria.workflow.dto.FlowDefinitionDetailResponse;
import io.github.skr416.atria.workflow.dto.FlowDefinitionResponse;
import io.github.skr416.atria.workflow.dto.FlowNodeUpsertRequest;
import io.github.skr416.atria.workflow.dto.FormTemplateResponse;
import io.github.skr416.atria.workflow.dto.FormTemplateUpsertRequest;
import io.github.skr416.atria.workflow.dto.TaskActionRequest;
import io.github.skr416.atria.workflow.dto.TaskReassignRequest;
import io.github.skr416.atria.workflow.dto.WorkflowDeputyCreateRequest;
import io.github.skr416.atria.workflow.dto.WorkflowDeputyResponse;
import io.github.skr416.atria.workflow.dto.WorkflowInstanceDetailResponse;
import io.github.skr416.atria.workflow.dto.WorkflowInstanceResponse;
import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;
import io.github.skr416.atria.workflow.service.FormTemplateAndFlowService;
import io.github.skr416.atria.workflow.service.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Unified REST controller for dynamic form templates, approval flow definitions,
 * deputy delegation periods, and multi-level approval workflow instances (ADR-0005 & ADR-0008).
 */
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow - Forms & Multi-Level Approvals", description = "Endpoints for dynamic form templates, flow definitions, deputy delegations, and workflow instances")
public class WorkflowController {

    private final FormTemplateAndFlowService formTemplateAndFlowService;
    private final WorkflowEngineService workflowEngineService;

    // ==========================================
    // Form Templates
    // ==========================================

    @Operation(summary = "List dynamic form templates")
    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('wf:template:read', 'wf:instance:self')")
    public ApiResponse<List<FormTemplateResponse>> listTemplates(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        return ApiResponse.ok(formTemplateAndFlowService.listTemplates(enabledOnly));
    }

    @Operation(summary = "Create dynamic form template")
    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('wf:template:write')")
    public ApiResponse<FormTemplateResponse> createTemplate(
            @Valid @RequestBody FormTemplateUpsertRequest req
    ) {
        return ApiResponse.ok(formTemplateAndFlowService.createTemplate(req));
    }

    @Operation(summary = "Update dynamic form template and bump version if schema changed")
    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('wf:template:write')")
    public ApiResponse<FormTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody FormTemplateUpsertRequest req
    ) {
        return ApiResponse.ok(formTemplateAndFlowService.updateTemplate(id, req));
    }

    // ==========================================
    // Flow Definitions & Nodes/Conditions
    // ==========================================

    @Operation(summary = "List workflow definitions")
    @GetMapping("/flows")
    @PreAuthorize("hasAnyAuthority('wf:template:read', 'wf:instance:self')")
    public ApiResponse<List<FlowDefinitionResponse>> listFlows() {
        return ApiResponse.ok(formTemplateAndFlowService.listFlowDefinitions());
    }

    @Operation(summary = "Get workflow definition detail including ordered nodes and activation conditions")
    @GetMapping("/flows/{id}")
    @PreAuthorize("hasAnyAuthority('wf:template:read', 'wf:instance:self')")
    public ApiResponse<FlowDefinitionDetailResponse> getFlowDetail(@PathVariable Long id) {
        return ApiResponse.ok(formTemplateAndFlowService.getFlowDefinitionDetail(id));
    }

    @Operation(summary = "Update flow nodes and structured activation conditions")
    @PutMapping("/flows/{id}/nodes")
    @PreAuthorize("hasAuthority('wf:template:write')")
    public ApiResponse<FlowDefinitionDetailResponse> updateFlowNodes(
            @PathVariable Long id,
            @RequestBody List<FlowNodeUpsertRequest> nodeRequests
    ) {
        return ApiResponse.ok(formTemplateAndFlowService.updateFlowNodesAndConditions(id, nodeRequests));
    }

    // ==========================================
    // Deputy Delegation
    // ==========================================

    @Operation(summary = "List deputy delegations for current user (as delegator or deputy)")
    @GetMapping("/deputies/me")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<List<WorkflowDeputyResponse>> listMyDeputies() {
        Long currentUserId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(formTemplateAndFlowService.listMyDeputies(currentUserId));
    }

    @Operation(summary = "List all deputy delegations (HR / Admin)")
    @GetMapping("/deputies")
    @PreAuthorize("hasAuthority('wf:template:read')")
    public ApiResponse<List<WorkflowDeputyResponse>> listAllDeputies() {
        return ApiResponse.ok(formTemplateAndFlowService.listAllDeputies());
    }

    @Operation(summary = "Create deputy delegation rule")
    @PostMapping("/deputies")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowDeputyResponse> createDeputy(
            @Valid @RequestBody WorkflowDeputyCreateRequest req
    ) {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long delegatorId = principal.getUserId();
        if (req.delegatorUserId() != null && !req.delegatorUserId().equals(principal.getUserId())) {
            if (!principal.getPermissionCodes().contains("wf:instance:reassign")) {
                throw new io.github.skr416.atria.common.exception.BusinessException(io.github.skr416.atria.common.api.ErrorCode.FORBIDDEN);
            }
            delegatorId = req.delegatorUserId();
        }
        return ApiResponse.ok(formTemplateAndFlowService.createDeputy(delegatorId, req));
    }

    @Operation(summary = "Disable deputy delegation rule")
    @PutMapping("/deputies/{id}/disable")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<Void> disableDeputy(@PathVariable Long id) {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean isAdmin = principal.getPermissionCodes().contains("wf:instance:reassign");
        formTemplateAndFlowService.disableDeputy(id, principal.getUserId(), isAdmin);
        return ApiResponse.ok();
    }

    // ==========================================
    // Workflow Instances & Actions
    // ==========================================

    @Operation(summary = "Save dynamic form draft")
    @PostMapping("/instances/draft")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> saveDraft(
            @Valid @RequestBody DynamicFormSubmitRequest dto
    ) {
        Long applicantId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(workflowEngineService.saveDraftDynamicForm(applicantId, dto));
    }

    @Operation(summary = "Submit or resubmit dynamic form")
    @PostMapping("/instances/submit")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> submit(
            @Valid @RequestBody DynamicFormSubmitRequest dto
    ) {
        Long applicantId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(workflowEngineService.submitDynamicForm(applicantId, dto));
    }

    @Operation(summary = "Get pending tasks in current user's approval inbox (including deputy delegations)")
    @GetMapping("/instances/inbox")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<List<WorkflowInstanceResponse>> getInbox() {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(workflowEngineService.listPendingInbox(actorUserId));
    }

    @Operation(summary = "Get current user's submitted and draft workflow instances")
    @GetMapping("/instances/my")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<List<WorkflowInstanceResponse>> getMyInstances() {
        Long applicantId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(workflowEngineService.listMyInstances(applicantId));
    }

    @Operation(summary = "List all workflow instances with optional status and targetType filter (HR / Admin)")
    @GetMapping("/instances")
    @PreAuthorize("hasAuthority('wf:instance:reassign')")
    public ApiResponse<List<WorkflowInstanceResponse>> getAllInstances(
            @RequestParam(required = false) WorkflowInstanceStatus status,
            @RequestParam(required = false) WorkflowTargetType targetType
    ) {
        return ApiResponse.ok(workflowEngineService.listAllInstances(status, targetType));
    }

    @Operation(summary = "Get workflow instance detail, task logs, snapshot, and return-to-draft diff")
    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAnyAuthority('wf:instance:self', 'wf:template:read')")
    public ApiResponse<WorkflowInstanceDetailResponse> getInstanceDetail(@PathVariable Long id) {
        return ApiResponse.ok(workflowEngineService.getInstanceDetail(id));
    }

    @Operation(summary = "Approve current workflow step")
    @PostMapping("/instances/{id}/approve")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> approveTask(
            @PathVariable Long id,
            @RequestBody(required = false) TaskActionRequest req
    ) {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        String comment = req != null ? req.comment() : null;
        return ApiResponse.ok(workflowEngineService.approveTask(id, actorUserId, comment));
    }

    @Operation(summary = "Reject current workflow step")
    @PostMapping("/instances/{id}/reject")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> rejectTask(
            @PathVariable Long id,
            @RequestBody(required = false) TaskActionRequest req
    ) {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        String comment = req != null ? req.comment() : null;
        return ApiResponse.ok(workflowEngineService.rejectTask(id, actorUserId, comment));
    }

    @Operation(summary = "Return dynamic form to draft for revision (差戻し)")
    @PostMapping("/instances/{id}/return-to-draft")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> returnToDraft(
            @PathVariable Long id,
            @RequestBody(required = false) TaskActionRequest req
    ) {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        String comment = req != null ? req.comment() : null;
        return ApiResponse.ok(workflowEngineService.returnToDraftTask(id, actorUserId, comment));
    }

    @Operation(summary = "Applicant withdraws pending workflow instance")
    @PostMapping("/instances/{id}/cancel")
    @PreAuthorize("hasAuthority('wf:instance:self')")
    public ApiResponse<WorkflowInstanceResponse> cancelInstance(
            @PathVariable Long id,
            @RequestBody(required = false) TaskActionRequest req
    ) {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        String comment = req != null ? req.comment() : null;
        return ApiResponse.ok(workflowEngineService.cancelInstance(id, actorUserId, comment));
    }

    @Operation(summary = "Revoke approved workflow instance")
    @PostMapping("/instances/{id}/revoke")
    @PreAuthorize("hasAnyAuthority('wf:instance:self', 'wf:instance:reassign')")
    public ApiResponse<WorkflowInstanceResponse> revokeInstance(
            @PathVariable Long id,
            @RequestBody(required = false) TaskActionRequest req
    ) {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean hasReassign = principal.getPermissionCodes().contains("wf:instance:reassign");
        String reason = req != null ? req.comment() : null;
        return ApiResponse.ok(workflowEngineService.revokeApprovedInstance(id, principal.getUserId(), hasReassign, reason));
    }

    @Operation(summary = "HR force reassign pending task to another user")
    @PostMapping("/instances/{id}/reassign")
    @PreAuthorize("hasAuthority('wf:instance:reassign')")
    public ApiResponse<WorkflowInstanceResponse> reassignTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskReassignRequest req
    ) {
        Long actorUserId = SecurityUtils.requirePrincipal().getUserId();
        return ApiResponse.ok(workflowEngineService.reassignTask(id, actorUserId, req.newApproverUserId(), req.comment()));
    }
}
