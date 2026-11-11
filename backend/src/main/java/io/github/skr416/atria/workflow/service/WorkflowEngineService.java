package io.github.skr416.atria.workflow.service;

import io.github.skr416.atria.attendance.event.AttRequestApprovedEvent;
import io.github.skr416.atria.attendance.event.AttRequestCancelledEvent;
import io.github.skr416.atria.attendance.event.AttRequestRejectedEvent;
import io.github.skr416.atria.attendance.event.AttRequestRevokedEvent;
import io.github.skr416.atria.attendance.event.AttRequestSubmittedEvent;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import io.github.skr416.atria.attendance.repository.AttendanceRequestRepository;
import io.github.skr416.atria.attendance.service.AttRequestGuard;
import io.github.skr416.atria.attendance.service.AttendanceRequestService;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.notification.dto.NotificationSendCommand;
import io.github.skr416.atria.notification.model.NotificationCategory;
import io.github.skr416.atria.notification.service.NotificationService;
import io.github.skr416.atria.workflow.dto.DynamicFormSubmitRequest;
import io.github.skr416.atria.workflow.dto.WorkflowInstanceDetailResponse;
import io.github.skr416.atria.workflow.dto.WorkflowInstanceResponse;
import io.github.skr416.atria.workflow.dto.WorkflowTaskLogResponse;
import io.github.skr416.atria.workflow.model.FlowDefinition;
import io.github.skr416.atria.workflow.model.FlowNode;
import io.github.skr416.atria.workflow.model.FlowNodeCondition;
import io.github.skr416.atria.workflow.model.FormTemplate;
import io.github.skr416.atria.workflow.model.WorkflowInstance;
import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;
import io.github.skr416.atria.workflow.model.WorkflowTaskAction;
import io.github.skr416.atria.workflow.model.WorkflowTaskLog;
import io.github.skr416.atria.workflow.repository.FlowDefinitionRepository;
import io.github.skr416.atria.workflow.repository.FlowNodeConditionRepository;
import io.github.skr416.atria.workflow.repository.FlowNodeRepository;
import io.github.skr416.atria.workflow.repository.FormTemplateRepository;
import io.github.skr416.atria.workflow.repository.WorkflowInstanceRepository;
import io.github.skr416.atria.workflow.repository.WorkflowTaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Core workflow engine service orchestrating attendance events, dynamic form submissions,
 * task approvals/rejections/returns, cancellations, HR reassignments, and notifications (ADR-0005 & ADR-0008).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowTaskLogRepository workflowTaskLogRepository;
    private final FormTemplateRepository formTemplateRepository;
    private final FlowDefinitionRepository flowDefinitionRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final FlowNodeConditionRepository flowNodeConditionRepository;
    private final StructuredConditionEvaluator structuredConditionEvaluator;
    private final ApproverResolverService approverResolverService;
    private final FormTemplateAndFlowService formTemplateAndFlowService;
    private final NotificationService notificationService;
    private final AttendanceRequestRepository attendanceRequestRepository;
    private final AttendanceRequestService attendanceRequestService;
    private final AttRequestGuard attRequestGuard;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==========================================
    // Event Listeners: Attendance Integration
    // ==========================================

    @EventListener
    @Transactional
    public void onAttRequestSubmitted(AttRequestSubmittedEvent event) {
        log.info("Received AttRequestSubmittedEvent for request #{} ({})", event.requestId(), event.requestType());

        WorkflowTargetType targetType = mapAttendanceRequestType(event.requestType());
        FlowDefinition flow = flowDefinitionRepository.findByTargetTypeAndEnabledTrue(targetType)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_FLOW_DEF_NOT_FOUND));

        Map<String, Object> formData = new HashMap<>();
        formData.put("requestId", event.requestId());
        formData.put("requestType", event.requestType().name());
        formData.put("leaveTypeCode", event.leaveTypeCode());
        formData.put("startDate", event.startDate() != null ? event.startDate().toString() : null);
        formData.put("endDate", event.endDate() != null ? event.endDate().toString() : null);
        formData.put("requestedMinutes", event.requestedMinutes());

        attendanceRequestRepository.findById(event.requestId()).ifPresent(req -> {
            formData.put("weightedMinutes", req.getWeightedMinutes());
            formData.put("reason", req.getReason());
            if (req.getOvertimeCompType() != null) {
                formData.put("overtimeCompType", req.getOvertimeCompType().name());
            }
            if (req.getCompLeaveMode() != null) {
                formData.put("compLeaveMode", req.getCompLeaveMode().name());
            }
        });

        String instanceNo = generateInstanceNo("WF-ATT");
        WorkflowInstance instance = new WorkflowInstance();
        instance.setInstanceNo(instanceNo);
        instance.setFlowDefinitionId(flow.getId());
        instance.setTargetType(targetType);
        instance.setAttRequestId(event.requestId());
        instance.setFormTemplateCode(flow.getFormTemplateCode());
        instance.setApplicantId(event.userId());
        instance.setDepartmentId(event.departmentId());
        instance.setTitle("[Attendance] " + event.requestType().name() + " #" + event.requestId());
        instance.setStatus(WorkflowInstanceStatus.PENDING);
        instance.setFormDataJson(formData);
        instance.setSubmissionCount(1);
        instance.setSubmittedAt(event.submittedAt() != null ? event.submittedAt() : Instant.now());

        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        recordTaskLog(saved.getId(), 0,
                Map.of("zh-TW", "送出差勤申請", "en-US", "Submit Attendance Request", "ja-JP", "勤怠申請提出"),
                WorkflowTaskAction.SUBMIT, null, event.userId(), null, null,
                "Attendance request #" + event.requestId() + " submitted");

        routeToNextStep(saved, flow.getId(), 0, event.userId(), event.departmentId(), formData, event.userId(), "Initial Submission");
    }

    @EventListener
    @Transactional
    public void onAttRequestCancelled(AttRequestCancelledEvent event) {
        log.info("Received AttRequestCancelledEvent for request #{}", event.requestId());

        workflowInstanceRepository.findByAttRequestId(event.requestId()).ifPresent(instance -> {
            if (instance.getStatus() == WorkflowInstanceStatus.PENDING) {
                instance.setStatus(WorkflowInstanceStatus.CANCELLED);
                instance.setCurrentApproverId(null);
                instance.setCurrentStepOrder(null);
                instance.setCompletedAt(event.cancelledAt() != null ? event.cancelledAt() : Instant.now());
                workflowInstanceRepository.save(instance);

                recordTaskLog(instance.getId(),
                        instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                        Map.of("zh-TW", "撤回申請", "en-US", "Cancel Request", "ja-JP", "申請取消"),
                        WorkflowTaskAction.CANCEL, null, event.userId(), null, null,
                        "Attendance request #" + event.requestId() + " cancelled by applicant");
            }
        });
    }

    private WorkflowTargetType mapAttendanceRequestType(AttendanceRequestType type) {
        return switch (type) {
            case LEAVE -> WorkflowTargetType.ATT_LEAVE;
            case OVERTIME -> WorkflowTargetType.ATT_OVERTIME;
            case PUNCH_CORRECTION -> WorkflowTargetType.ATT_PUNCH_CORRECTION;
        };
    }

    // ==========================================
    // Dynamic Form Draft & Submission
    // ==========================================

    @Transactional
    public WorkflowInstanceResponse saveDraftDynamicForm(Long applicantId, DynamicFormSubmitRequest dto) {
        FormTemplate template = formTemplateRepository.findByCode(dto.formTemplateCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_TEMPLATE_NOT_FOUND));

        if (dto.instanceId() != null) {
            WorkflowInstance instance = workflowInstanceRepository.findById(dto.instanceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

            if (!Objects.equals(instance.getApplicantId(), applicantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (instance.getStatus() != WorkflowInstanceStatus.DRAFT) {
                throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
            }

            instance.setTitle(dto.title());
            instance.setFormDataJson(dto.formData());
            WorkflowInstance saved = workflowInstanceRepository.save(instance);
            return toInstanceResponse(saved);
        } else {
            FlowDefinition flow = flowDefinitionRepository
                    .findByTargetTypeAndFormTemplateCodeAndEnabledTrue(WorkflowTargetType.DYNAMIC_FORM, dto.formTemplateCode())
                    .orElseGet(() -> flowDefinitionRepository.findByTargetTypeAndEnabledTrue(WorkflowTargetType.DYNAMIC_FORM)
                            .orElseThrow(() -> new BusinessException(ErrorCode.WF_FLOW_DEF_NOT_FOUND)));

            User user = userRepository.findById(applicantId).orElse(null);
            Long deptId = user != null ? user.getDepartmentId() : null;

            WorkflowInstance instance = new WorkflowInstance();
            instance.setInstanceNo(generateInstanceNo("WF-DF"));
            instance.setFlowDefinitionId(flow.getId());
            instance.setTargetType(WorkflowTargetType.DYNAMIC_FORM);
            instance.setFormTemplateCode(dto.formTemplateCode());
            instance.setFormVersion(template.getCurrentVersion());
            instance.setSchemaSnapshotJson(template.getSchemaJson());
            instance.setApplicantId(applicantId);
            instance.setDepartmentId(deptId);
            instance.setTitle(dto.title());
            instance.setStatus(WorkflowInstanceStatus.DRAFT);
            instance.setFormDataJson(dto.formData());
            instance.setSubmissionCount(1);

            WorkflowInstance saved = workflowInstanceRepository.save(instance);
            return toInstanceResponse(saved);
        }
    }

    @Transactional
    public WorkflowInstanceResponse submitDynamicForm(Long applicantId, DynamicFormSubmitRequest dto) {
        FormTemplate template = formTemplateRepository.findByCode(dto.formTemplateCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_TEMPLATE_NOT_FOUND));

        formTemplateAndFlowService.validateFormData(template.getSchemaJson(), dto.formData());

        FlowDefinition flow = flowDefinitionRepository
                .findByTargetTypeAndFormTemplateCodeAndEnabledTrue(WorkflowTargetType.DYNAMIC_FORM, dto.formTemplateCode())
                .orElseGet(() -> flowDefinitionRepository.findByTargetTypeAndEnabledTrue(WorkflowTargetType.DYNAMIC_FORM)
                        .orElseThrow(() -> new BusinessException(ErrorCode.WF_FLOW_DEF_NOT_FOUND)));

        WorkflowTaskAction taskAction = WorkflowTaskAction.SUBMIT;
        WorkflowInstance instance;

        if (dto.instanceId() != null) {
            instance = workflowInstanceRepository.findById(dto.instanceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

            if (!Objects.equals(instance.getApplicantId(), applicantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (instance.getStatus() != WorkflowInstanceStatus.DRAFT) {
                throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
            }

            if (instance.getPreviousFormDataJson() != null) {
                List<Map<String, Object>> diff = computeFormDiff(instance.getPreviousFormDataJson(), dto.formData());
                instance.setDiffJson(diff);
                instance.setSubmissionCount(instance.getSubmissionCount() + 1);
                taskAction = WorkflowTaskAction.RESUBMIT;
            }

            instance.setTitle(dto.title());
            instance.setFormDataJson(dto.formData());
            instance.setStatus(WorkflowInstanceStatus.PENDING);
            instance.setSubmittedAt(Instant.now());
            instance.setFormVersion(template.getCurrentVersion());
            instance.setSchemaSnapshotJson(template.getSchemaJson());
            instance = workflowInstanceRepository.save(instance);
        } else {
            User user = userRepository.findById(applicantId).orElse(null);
            Long deptId = user != null ? user.getDepartmentId() : null;

            instance = new WorkflowInstance();
            instance.setInstanceNo(generateInstanceNo("WF-DF"));
            instance.setFlowDefinitionId(flow.getId());
            instance.setTargetType(WorkflowTargetType.DYNAMIC_FORM);
            instance.setFormTemplateCode(dto.formTemplateCode());
            instance.setFormVersion(template.getCurrentVersion());
            instance.setSchemaSnapshotJson(template.getSchemaJson());
            instance.setApplicantId(applicantId);
            instance.setDepartmentId(deptId);
            instance.setTitle(dto.title());
            instance.setStatus(WorkflowInstanceStatus.PENDING);
            instance.setFormDataJson(dto.formData());
            instance.setSubmissionCount(1);
            instance.setSubmittedAt(Instant.now());
            instance = workflowInstanceRepository.save(instance);
        }

        recordTaskLog(instance.getId(), 0,
                Map.of("zh-TW", taskAction == WorkflowTaskAction.RESUBMIT ? "重新送出表單" : "送出表單",
                        "en-US", taskAction == WorkflowTaskAction.RESUBMIT ? "Resubmit Form" : "Submit Form",
                        "ja-JP", taskAction == WorkflowTaskAction.RESUBMIT ? "フォーム再提出" : "フォーム提出"),
                taskAction, null, applicantId, null, null, null);

        routeToNextStep(instance, flow.getId(), 0, applicantId, instance.getDepartmentId(), dto.formData(), applicantId, "Form Submitted");

        return toInstanceResponse(instance);
    }

    private List<Map<String, Object>> computeFormDiff(Map<String, Object> oldData, Map<String, Object> newData) {
        List<Map<String, Object>> diffList = new ArrayList<>();
        Map<String, Object> oldMap = oldData != null ? oldData : Map.of();
        Map<String, Object> newMap = newData != null ? newData : Map.of();

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());

        for (String key : allKeys) {
            Object oldVal = oldMap.get(key);
            Object newVal = newMap.get(key);
            if (!Objects.equals(oldVal, newVal)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("fieldKey", key);
                entry.put("oldValue", oldVal);
                entry.put("newValue", newVal);
                diffList.add(entry);
            }
        }
        return diffList;
    }

    // ==========================================
    // Approver Tasks: Approve, Reject, Return-to-Draft
    // ==========================================

    @Transactional
    public WorkflowInstanceResponse approveTask(Long instanceId, Long actorUserId, String comment) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.PENDING) {
            throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
        }

        FlowNode currentNode = findCurrentNode(instance.getFlowDefinitionId(), instance.getCurrentStepOrder());
        boolean allowDeputy = currentNode != null ? currentNode.isAllowDeputy() : true;

        Optional<Long> delegatorIdOpt = approverResolverService.verifyApproverOrDeputy(
                instance.getCurrentApproverId(), actorUserId, allowDeputy, LocalDate.now()
        );

        recordTaskLog(instance.getId(),
                instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                currentNode != null ? currentNode.getName() : Map.of("zh-TW", "審核", "en-US", "Approval", "ja-JP", "承認"),
                WorkflowTaskAction.APPROVE,
                instance.getCurrentApproverId(),
                actorUserId,
                delegatorIdOpt.orElse(null),
                null,
                comment);

        routeToNextStep(instance, instance.getFlowDefinitionId(),
                instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                instance.getApplicantId(), instance.getDepartmentId(), instance.getFormDataJson(),
                actorUserId, comment);

        return toInstanceResponse(instance);
    }

    @Transactional
    public WorkflowInstanceResponse rejectTask(Long instanceId, Long actorUserId, String comment) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.PENDING) {
            throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
        }

        FlowNode currentNode = findCurrentNode(instance.getFlowDefinitionId(), instance.getCurrentStepOrder());
        boolean allowDeputy = currentNode != null ? currentNode.isAllowDeputy() : true;

        Optional<Long> delegatorIdOpt = approverResolverService.verifyApproverOrDeputy(
                instance.getCurrentApproverId(), actorUserId, allowDeputy, LocalDate.now()
        );

        recordTaskLog(instance.getId(),
                instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                currentNode != null ? currentNode.getName() : Map.of("zh-TW", "駁回", "en-US", "Reject", "ja-JP", "却下"),
                WorkflowTaskAction.REJECT,
                instance.getCurrentApproverId(),
                actorUserId,
                delegatorIdOpt.orElse(null),
                null,
                comment);

        instance.setStatus(WorkflowInstanceStatus.REJECTED);
        instance.setCompletedAt(Instant.now());
        instance.setCurrentApproverId(null);
        instance.setCurrentStepOrder(null);
        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        if (saved.getAttRequestId() != null) {
            attendanceRequestRepository.findById(saved.getAttRequestId()).ifPresent(req -> {
                if (req.getStatus() == AttendanceRequestStatus.PENDING) {
                    eventPublisher.publishEvent(new AttRequestRejectedEvent(req.getId(), actorUserId, comment, Instant.now()));
                }
            });
        }

        sendWorkflowNotification(
                saved.getApplicantId(),
                Map.of("zh-TW", "申請單已被駁回", "en-US", "Request Rejected", "ja-JP", "申請却下"),
                Map.of("zh-TW", "您的單據 " + saved.getInstanceNo() + " 已被駁回。原因: " + (comment != null ? comment : "無"),
                        "en-US", "Your request " + saved.getInstanceNo() + " was rejected. Reason: " + (comment != null ? comment : "None"),
                        "ja-JP", "申請 " + saved.getInstanceNo() + " は却下されました。理由: " + (comment != null ? comment : "なし")),
                saved.getId()
        );

        return toInstanceResponse(saved);
    }

    @Transactional
    public WorkflowInstanceResponse returnToDraftTask(Long instanceId, Long actorUserId, String comment) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.PENDING) {
            throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
        }
        if (instance.getAttRequestId() != null) {
            throw new BusinessException(ErrorCode.WF_RETURN_TO_DRAFT_NOT_ALLOWED_FOR_ATT_REQUEST);
        }

        FlowNode currentNode = findCurrentNode(instance.getFlowDefinitionId(), instance.getCurrentStepOrder());
        boolean allowDeputy = currentNode != null ? currentNode.isAllowDeputy() : true;

        Optional<Long> delegatorIdOpt = approverResolverService.verifyApproverOrDeputy(
                instance.getCurrentApproverId(), actorUserId, allowDeputy, LocalDate.now()
        );

        recordTaskLog(instance.getId(),
                instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                currentNode != null ? currentNode.getName() : Map.of("zh-TW", "差戻し", "en-US", "Return to Draft", "ja-JP", "差戻し"),
                WorkflowTaskAction.RETURN_TO_DRAFT,
                instance.getCurrentApproverId(),
                actorUserId,
                delegatorIdOpt.orElse(null),
                null,
                comment);

        instance.setPreviousFormDataJson(new HashMap<>(instance.getFormDataJson()));
        instance.setStatus(WorkflowInstanceStatus.DRAFT);
        instance.setCurrentStepOrder(null);
        instance.setCurrentApproverId(null);
        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        sendWorkflowNotification(
                saved.getApplicantId(),
                Map.of("zh-TW", "申請單已退回草稿", "en-US", "Request Returned to Draft", "ja-JP", "申請差戻し"),
                Map.of("zh-TW", "您的單據 " + saved.getInstanceNo() + " 已被審核人退回草稿。意見: " + (comment != null ? comment : "無"),
                        "en-US", "Your request " + saved.getInstanceNo() + " has been returned to draft. Comment: " + (comment != null ? comment : "None"),
                        "ja-JP", "申請 " + saved.getInstanceNo() + " は差戻されました。コメント: " + (comment != null ? comment : "なし")),
                saved.getId()
        );

        return toInstanceResponse(saved);
    }

    // ==========================================
    // Applicant Cancel / Withdraw
    // ==========================================

    @Transactional
    public WorkflowInstanceResponse cancelInstance(Long instanceId, Long actorUserId, String comment) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.PENDING) {
            throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
        }
        if (!Objects.equals(instance.getApplicantId(), actorUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (instance.getAttRequestId() != null) {
            attendanceRequestService.cancelRequest(instance.getAttRequestId(), actorUserId);
            // onAttRequestCancelled listener handles wf_instance transition to CANCELLED
            WorkflowInstance reloaded = workflowInstanceRepository.findById(instanceId).orElse(instance);
            return toInstanceResponse(reloaded);
        } else {
            instance.setStatus(WorkflowInstanceStatus.DRAFT);
            instance.setCurrentStepOrder(null);
            instance.setCurrentApproverId(null);
            WorkflowInstance saved = workflowInstanceRepository.save(instance);

            recordTaskLog(saved.getId(), 0,
                    Map.of("zh-TW", "申請人撤回", "en-US", "Withdraw", "ja-JP", "申請取下"),
                    WorkflowTaskAction.CANCEL, null, actorUserId, null, null, comment);

            List<WorkflowTaskLog> logs = workflowTaskLogRepository.findByInstanceIdOrderByActedAtAscIdAsc(saved.getId());
            Set<Long> previousApproverIds = new LinkedHashSet<>();
            for (WorkflowTaskLog l : logs) {
                if (l.getAction() == WorkflowTaskAction.APPROVE && l.getActorUserId() != null) {
                    previousApproverIds.add(l.getActorUserId());
                }
            }
            for (Long approverUserId : previousApproverIds) {
                sendWorkflowNotification(
                        approverUserId,
                        Map.of("zh-TW", "單據撤回通知: " + saved.getTitle(),
                                "en-US", "Request Withdrawn: " + saved.getTitle(),
                                "ja-JP", "申請取下通知: " + saved.getTitle()),
                        Map.of("zh-TW", "申請人已撤回單據 " + saved.getInstanceNo() + "。",
                                "en-US", "Applicant withdrew request " + saved.getInstanceNo() + ".",
                                "ja-JP", "申請者により申請 " + saved.getInstanceNo() + " が取下げられました。"),
                        saved.getId()
                );
            }

            return toInstanceResponse(saved);
        }
    }

    // ==========================================
    // Revocation & HR Force Reassign
    // ==========================================

    @Transactional
    public WorkflowInstanceResponse revokeApprovedInstance(Long instanceId, Long actorUserId, boolean hasReassignPermission, String reason) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.APPROVED) {
            throw new BusinessException(ErrorCode.WF_CANNOT_REVOKE_APPROVED);
        }

        boolean isApplicant = Objects.equals(instance.getApplicantId(), actorUserId);
        if (!isApplicant && !hasReassignPermission) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (instance.getAttRequestId() != null) {
            AttendanceRequest attReq = attendanceRequestRepository.findById(instance.getAttRequestId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND));
            attRequestGuard.assertCanRevokeApprovedRequest(attReq);
            eventPublisher.publishEvent(new AttRequestRevokedEvent(attReq.getId(), actorUserId, reason, Instant.now()));
        }

        instance.setStatus(WorkflowInstanceStatus.REVOKED);
        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        recordTaskLog(saved.getId(), 0,
                Map.of("zh-TW", "廢止/註銷已核准單據", "en-US", "Revoke Approved Request", "ja-JP", "承認済み申請取消"),
                WorkflowTaskAction.REVOKE, null, actorUserId, null, null, reason);

        sendWorkflowNotification(
                saved.getApplicantId(),
                Map.of("zh-TW", "單據已廢止: " + saved.getTitle(),
                        "en-US", "Request Revoked: " + saved.getTitle(),
                        "ja-JP", "申請取消: " + saved.getTitle()),
                Map.of("zh-TW", "已核准單據 " + saved.getInstanceNo() + " 已被註銷/廢止。事由: " + (reason != null ? reason : "無"),
                        "en-US", "Approved request " + saved.getInstanceNo() + " has been revoked. Reason: " + (reason != null ? reason : "None"),
                        "ja-JP", "承認済み申請 " + saved.getInstanceNo() + " は取消されました。理由: " + (reason != null ? reason : "なし")),
                saved.getId()
        );

        return toInstanceResponse(saved);
    }

    @Transactional
    public WorkflowInstanceResponse reassignTask(Long instanceId, Long actorUserId, Long newApproverUserId, String comment) {
        WorkflowInstance instance = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        if (instance.getStatus() != WorkflowInstanceStatus.PENDING) {
            throw new BusinessException(ErrorCode.WF_INVALID_STATE_TRANSITION);
        }

        User newUser = userRepository.findById(newApproverUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!newUser.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        Long oldApproverId = instance.getCurrentApproverId();
        FlowNode currentNode = findCurrentNode(instance.getFlowDefinitionId(), instance.getCurrentStepOrder());

        recordTaskLog(instance.getId(),
                instance.getCurrentStepOrder() != null ? instance.getCurrentStepOrder() : 0,
                currentNode != null ? currentNode.getName() : Map.of("zh-TW", "管理改派", "en-US", "Force Reassign", "ja-JP", "管理者再割当"),
                WorkflowTaskAction.REASSIGN,
                oldApproverId,
                actorUserId,
                null,
                newApproverUserId,
                comment);

        instance.setCurrentApproverId(newApproverUserId);
        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        sendWorkflowNotification(
                newApproverUserId,
                Map.of("zh-TW", "簽核單據已改派給您: " + saved.getTitle(),
                        "en-US", "Approval Task Reassigned to You: " + saved.getTitle(),
                        "ja-JP", "承認タスク再割当通知: " + saved.getTitle()),
                Map.of("zh-TW", "管理員已將單據 " + saved.getInstanceNo() + " 改派至您的待簽核清單。",
                        "en-US", "Admin reassigned task " + saved.getInstanceNo() + " to your approval inbox.",
                        "ja-JP", "管理者により申請 " + saved.getInstanceNo() + " があなたの受信箱に再割当されました。"),
                saved.getId()
        );

        return toInstanceResponse(saved);
    }

    // ==========================================
    // Query & Inbox
    // ==========================================

    @Transactional(readOnly = true)
    public List<WorkflowInstanceResponse> listPendingInbox(Long actorUserId) {
        List<WorkflowInstance> myDirectTasks = workflowInstanceRepository
                .findByStatusAndCurrentApproverIdOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus.PENDING, actorUserId);

        List<Long> delegatorIds = approverResolverService.findDelegatorIdsForDeputy(actorUserId, LocalDate.now());
        List<WorkflowInstance> deputyTasks = List.of();
        if (!delegatorIds.isEmpty()) {
            List<WorkflowInstance> candidates = workflowInstanceRepository
                    .findByStatusAndCurrentApproverIdInOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus.PENDING, delegatorIds);

            deputyTasks = candidates.stream().filter(inst -> {
                FlowNode node = findCurrentNode(inst.getFlowDefinitionId(), inst.getCurrentStepOrder());
                return node == null || node.isAllowDeputy();
            }).toList();
        }

        Map<Long, WorkflowInstance> merged = new LinkedHashMap<>();
        for (WorkflowInstance inst : myDirectTasks) {
            merged.put(inst.getId(), inst);
        }
        for (WorkflowInstance inst : deputyTasks) {
            merged.putIfAbsent(inst.getId(), inst);
        }

        return merged.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toInstanceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstanceResponse> listMyInstances(Long applicantId) {
        return workflowInstanceRepository.findByApplicantIdOrderByCreatedAtDescIdDesc(applicantId).stream()
                .map(this::toInstanceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstanceResponse> listAllInstances(WorkflowInstanceStatus status, WorkflowTargetType targetType) {
        List<WorkflowInstance> list;
        if (status != null && targetType != null) {
            list = workflowInstanceRepository.findByStatusAndTargetTypeOrderByCreatedAtDescIdDesc(status, targetType);
        } else if (status != null) {
            list = workflowInstanceRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
        } else if (targetType != null) {
            list = workflowInstanceRepository.findByTargetTypeOrderByCreatedAtDescIdDesc(targetType);
        } else {
            list = workflowInstanceRepository.findAllByOrderByCreatedAtDescIdDesc();
        }
        return list.stream().map(this::toInstanceResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowInstanceDetailResponse getInstanceDetail(Long instanceId) {
        WorkflowInstance inst = workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_INSTANCE_NOT_FOUND));

        List<WorkflowTaskLog> logs = workflowTaskLogRepository.findByInstanceIdOrderByActedAtAscIdAsc(instanceId);
        List<WorkflowTaskLogResponse> logResponses = logs.stream().map(this::toTaskLogResponse).toList();

        String applicantName = userRepository.findById(inst.getApplicantId())
                .map(User::getDisplayName)
                .orElse("User#" + inst.getApplicantId());

        String departmentName = inst.getDepartmentId() != null
                ? departmentRepository.findById(inst.getDepartmentId())
                .map(d -> d.getName() != null ? d.getName().get("zh-TW") : null)
                .orElse(null)
                : null;

        String currentApproverName = inst.getCurrentApproverId() != null
                ? userRepository.findById(inst.getCurrentApproverId())
                .map(User::getDisplayName)
                .orElse("User#" + inst.getCurrentApproverId())
                : null;

        return new WorkflowInstanceDetailResponse(
                inst.getId(),
                inst.getInstanceNo(),
                inst.getFlowDefinitionId(),
                inst.getTargetType(),
                inst.getAttRequestId(),
                inst.getFormTemplateCode(),
                inst.getFormVersion(),
                inst.getApplicantId(),
                applicantName,
                inst.getDepartmentId(),
                departmentName,
                inst.getTitle(),
                inst.getStatus(),
                inst.getCurrentStepOrder(),
                inst.getCurrentApproverId(),
                currentApproverName,
                inst.getSubmissionCount(),
                inst.getFormDataJson(),
                inst.getPreviousFormDataJson(),
                inst.getDiffJson(),
                inst.getSchemaSnapshotJson(),
                logResponses,
                inst.getSubmittedAt(),
                inst.getCompletedAt(),
                inst.getCreatedAt()
        );
    }

    // ==========================================
    // Internal Routing Engine
    // ==========================================

    private void routeToNextStep(WorkflowInstance instance, Long flowDefId, int currentStep,
                                 Long applicantId, Long applicantDeptId, Map<String, Object> formData,
                                 Long lastActorUserId, String lastComment) {
        List<FlowNode> allNodes = flowNodeRepository.findByFlowDefinitionIdOrderByStepOrderAsc(flowDefId);
        List<FlowNode> candidateNodes = allNodes.stream()
                .filter(n -> n.getStepOrder() > currentStep)
                .toList();

        List<Long> candidateNodeIds = candidateNodes.stream().map(FlowNode::getId).toList();
        List<FlowNodeCondition> conditions = candidateNodeIds.isEmpty()
                ? List.of()
                : flowNodeConditionRepository.findByFlowNodeIdInOrderByLogicalGroupAscIdAsc(candidateNodeIds);

        List<FlowNode> activeNodes = candidateNodes.stream().filter(node -> {
            List<FlowNodeCondition> nodeConds = conditions.stream()
                    .filter(c -> Objects.equals(c.getFlowNodeId(), node.getId()))
                    .toList();
            return structuredConditionEvaluator.evaluateNodeConditions(nodeConds, formData);
        }).toList();

        if (activeNodes.isEmpty()) {
            completeApproval(instance, lastActorUserId, lastComment);
            return;
        }

        for (int i = 0; i < activeNodes.size(); i++) {
            FlowNode node = activeNodes.get(i);
            boolean isLast = (i == activeNodes.size() - 1);
            Long approverId = approverResolverService.resolveApprover(node, applicantId, applicantDeptId, isLast);

            if (Objects.equals(approverId, applicantId) && node.isSelfApprovalAutoSkip() && !isLast) {
                recordTaskLog(instance.getId(), node.getStepOrder(), node.getName(),
                        WorkflowTaskAction.AUTO_SKIP, applicantId, applicantId, null, null,
                        "Self-approval auto-skipped (rule: selfApprovalAutoSkip)");
                continue;
            }

            instance.setCurrentStepOrder(node.getStepOrder());
            instance.setCurrentApproverId(approverId);
            workflowInstanceRepository.save(instance);

            sendWorkflowNotification(
                    approverId,
                    Map.of("zh-TW", "待簽核通知: " + instance.getTitle(),
                            "en-US", "Pending Approval: " + instance.getTitle(),
                            "ja-JP", "承認依頼: " + instance.getTitle()),
                    Map.of("zh-TW", "您有一筆待簽核單據 (" + instance.getInstanceNo() + ")，請盡速處理。",
                            "en-US", "You have a pending approval task (" + instance.getInstanceNo() + ").",
                            "ja-JP", "承認待ちの申請 (" + instance.getInstanceNo() + ") があります。"),
                    instance.getId()
            );
            return;
        }

        completeApproval(instance, lastActorUserId, lastComment);
    }

    private void completeApproval(WorkflowInstance instance, Long reviewerId, String comment) {
        instance.setStatus(WorkflowInstanceStatus.APPROVED);
        instance.setCompletedAt(Instant.now());
        instance.setCurrentStepOrder(null);
        instance.setCurrentApproverId(null);
        WorkflowInstance saved = workflowInstanceRepository.save(instance);

        if (saved.getAttRequestId() != null) {
            attendanceRequestRepository.findById(saved.getAttRequestId()).ifPresent(req -> {
                if (req.getStatus() == AttendanceRequestStatus.PENDING) {
                    eventPublisher.publishEvent(new AttRequestApprovedEvent(
                            req.getId(),
                            reviewerId != null ? reviewerId : saved.getApplicantId(),
                            comment != null ? comment : "Approved",
                            Instant.now()
                    ));
                }
            });
        }

        sendWorkflowNotification(
                saved.getApplicantId(),
                Map.of("zh-TW", "申請單簽核已核准", "en-US", "Request Approved", "ja-JP", "申請承認完了"),
                Map.of("zh-TW", "您的單據 " + saved.getInstanceNo() + " (" + saved.getTitle() + ") 已全數核准。",
                        "en-US", "Your request " + saved.getInstanceNo() + " (" + saved.getTitle() + ") has been approved.",
                        "ja-JP", "申請 " + saved.getInstanceNo() + " (" + saved.getTitle() + ") の承認が完了しました。"),
                saved.getId()
        );
    }

    private FlowNode findCurrentNode(Long flowDefId, Integer stepOrder) {
        if (flowDefId == null || stepOrder == null) {
            return null;
        }
        return flowNodeRepository.findByFlowDefinitionIdOrderByStepOrderAsc(flowDefId).stream()
                .filter(n -> Objects.equals(n.getStepOrder(), stepOrder))
                .findFirst()
                .orElse(null);
    }

    private void recordTaskLog(Long instanceId, int stepOrder, Map<String, String> nodeName,
                               WorkflowTaskAction action, Long assignedApproverId, Long actorUserId,
                               Long delegatorUserId, Long reassignedToUserId, String comment) {
        WorkflowTaskLog taskLog = new WorkflowTaskLog();
        taskLog.setInstanceId(instanceId);
        taskLog.setStepOrder(stepOrder);
        taskLog.setNodeName(nodeName != null ? nodeName : Map.of());
        taskLog.setAction(action);
        taskLog.setAssignedApproverId(assignedApproverId);
        taskLog.setActorUserId(actorUserId);
        taskLog.setDelegatorUserId(delegatorUserId);
        taskLog.setReassignedToUserId(reassignedToUserId);
        taskLog.setComment(comment);
        taskLog.setActedAt(Instant.now());
        workflowTaskLogRepository.save(taskLog);
    }

    private void sendWorkflowNotification(Long recipientUserId, Map<String, String> title, Map<String, String> content, Long instanceId) {
        if (recipientUserId == null) {
            return;
        }
        try {
            notificationService.sendNotification(new NotificationSendCommand(
                    recipientUserId,
                    NotificationCategory.WORKFLOW,
                    title,
                    content,
                    "WF_INSTANCE",
                    instanceId,
                    false
            ));
        } catch (Exception e) {
            log.warn("Failed to dispatch workflow notification to user #{}: {}", recipientUserId, e.getMessage());
        }
    }

    private String generateInstanceNo(String prefix) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuidSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return prefix + "-" + dateStr + "-" + uuidSuffix;
    }

    private WorkflowInstanceResponse toInstanceResponse(WorkflowInstance inst) {
        String applicantName = userRepository.findById(inst.getApplicantId())
                .map(User::getDisplayName)
                .orElse("User#" + inst.getApplicantId());

        String departmentName = inst.getDepartmentId() != null
                ? departmentRepository.findById(inst.getDepartmentId())
                .map(d -> d.getName() != null ? d.getName().get("zh-TW") : null)
                .orElse(null)
                : null;

        String currentApproverName = inst.getCurrentApproverId() != null
                ? userRepository.findById(inst.getCurrentApproverId())
                .map(User::getDisplayName)
                .orElse("User#" + inst.getCurrentApproverId())
                : null;

        return new WorkflowInstanceResponse(
                inst.getId(),
                inst.getInstanceNo(),
                inst.getFlowDefinitionId(),
                inst.getTargetType(),
                inst.getAttRequestId(),
                inst.getFormTemplateCode(),
                inst.getFormVersion(),
                inst.getApplicantId(),
                applicantName,
                inst.getDepartmentId(),
                departmentName,
                inst.getTitle(),
                inst.getStatus(),
                inst.getCurrentStepOrder(),
                inst.getCurrentApproverId(),
                currentApproverName,
                inst.getSubmissionCount(),
                inst.getSubmittedAt(),
                inst.getCompletedAt(),
                inst.getCreatedAt()
        );
    }

    private WorkflowTaskLogResponse toTaskLogResponse(WorkflowTaskLog l) {
        String assignedApproverName = l.getAssignedApproverId() != null
                ? userRepository.findById(l.getAssignedApproverId()).map(User::getDisplayName).orElse("User#" + l.getAssignedApproverId())
                : null;
        String actorUserName = userRepository.findById(l.getActorUserId())
                .map(User::getDisplayName)
                .orElse("User#" + l.getActorUserId());
        String delegatorUserName = l.getDelegatorUserId() != null
                ? userRepository.findById(l.getDelegatorUserId()).map(User::getDisplayName).orElse("User#" + l.getDelegatorUserId())
                : null;
        String reassignedToUserName = l.getReassignedToUserId() != null
                ? userRepository.findById(l.getReassignedToUserId()).map(User::getDisplayName).orElse("User#" + l.getReassignedToUserId())
                : null;

        return new WorkflowTaskLogResponse(
                l.getId(),
                l.getInstanceId(),
                l.getStepOrder(),
                l.getNodeName(),
                l.getAction(),
                l.getAssignedApproverId(),
                assignedApproverName,
                l.getActorUserId(),
                actorUserName,
                l.getDelegatorUserId(),
                delegatorUserName,
                l.getReassignedToUserId(),
                reassignedToUserName,
                l.getComment(),
                l.getActedAt()
        );
    }
}
