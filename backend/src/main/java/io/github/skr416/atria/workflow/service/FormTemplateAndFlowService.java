package io.github.skr416.atria.workflow.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.workflow.dto.FlowDefinitionDetailResponse;
import io.github.skr416.atria.workflow.dto.FlowDefinitionResponse;
import io.github.skr416.atria.workflow.dto.FlowNodeConditionResponse;
import io.github.skr416.atria.workflow.dto.FlowNodeConditionUpsertRequest;
import io.github.skr416.atria.workflow.dto.FlowNodeResponse;
import io.github.skr416.atria.workflow.dto.FlowNodeUpsertRequest;
import io.github.skr416.atria.workflow.dto.FormTemplateResponse;
import io.github.skr416.atria.workflow.dto.FormTemplateUpsertRequest;
import io.github.skr416.atria.workflow.dto.WorkflowDeputyCreateRequest;
import io.github.skr416.atria.workflow.dto.WorkflowDeputyResponse;
import io.github.skr416.atria.workflow.model.FlowDefinition;
import io.github.skr416.atria.workflow.model.FlowNode;
import io.github.skr416.atria.workflow.model.FlowNodeCondition;
import io.github.skr416.atria.workflow.model.FormTemplate;
import io.github.skr416.atria.workflow.model.WorkflowDeputy;
import io.github.skr416.atria.workflow.repository.FlowDefinitionRepository;
import io.github.skr416.atria.workflow.repository.FlowNodeConditionRepository;
import io.github.skr416.atria.workflow.repository.FlowNodeRepository;
import io.github.skr416.atria.workflow.repository.FormTemplateRepository;
import io.github.skr416.atria.workflow.repository.WorkflowDeputyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service managing dynamic form templates, approval flow definitions, nodes & structured conditions,
 * and deputy delegation periods (ADR-0005 Decisions 1, 2 & 3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormTemplateAndFlowService {

    private final FormTemplateRepository formTemplateRepository;
    private final FlowDefinitionRepository flowDefinitionRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final FlowNodeConditionRepository flowNodeConditionRepository;
    private final WorkflowDeputyRepository workflowDeputyRepository;
    private final UserRepository userRepository;

    // ==========================================
    // FormTemplate CRUD & Validation
    // ==========================================

    @Transactional(readOnly = true)
    public List<FormTemplateResponse> listTemplates(boolean enabledOnly) {
        List<FormTemplate> list = enabledOnly
                ? formTemplateRepository.findByEnabledTrueOrderByIdAsc()
                : formTemplateRepository.findAllByOrderByIdAsc();
        return list.stream().map(this::toFormTemplateResponse).toList();
    }

    @Transactional(readOnly = true)
    public FormTemplateResponse getTemplateById(Long id) {
        FormTemplate template = formTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_TEMPLATE_NOT_FOUND));
        return toFormTemplateResponse(template);
    }

    @Transactional(readOnly = true)
    public FormTemplateResponse getTemplateByCode(String code) {
        FormTemplate template = formTemplateRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_TEMPLATE_NOT_FOUND));
        return toFormTemplateResponse(template);
    }

    @Transactional
    public FormTemplateResponse createTemplate(FormTemplateUpsertRequest req) {
        if (formTemplateRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.WF_TEMPLATE_CODE_EXISTS);
        }
        FormTemplate template = new FormTemplate();
        template.setCode(req.code());
        template.setName(req.name());
        template.setCategory(req.category() != null ? req.category() : "GENERAL");
        template.setCurrentVersion(1);
        template.setSchemaJson(req.schemaJson());
        template.setEnabled(req.enabled() == null || req.enabled());
        template.setDescription(req.description());

        FormTemplate saved = formTemplateRepository.save(template);
        return toFormTemplateResponse(saved);
    }

    @Transactional
    public FormTemplateResponse updateTemplate(Long id, FormTemplateUpsertRequest req) {
        FormTemplate template = formTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_TEMPLATE_NOT_FOUND));

        if (!Objects.equals(template.getCode(), req.code()) && formTemplateRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.WF_TEMPLATE_CODE_EXISTS);
        }

        if (!Objects.equals(template.getSchemaJson(), req.schemaJson())) {
            template.setCurrentVersion(template.getCurrentVersion() + 1);
            template.setSchemaJson(req.schemaJson());
        }

        template.setCode(req.code());
        template.setName(req.name());
        if (req.category() != null) {
            template.setCategory(req.category());
        }
        if (req.enabled() != null) {
            template.setEnabled(req.enabled());
        }
        template.setDescription(req.description());

        FormTemplate saved = formTemplateRepository.save(template);
        return toFormTemplateResponse(saved);
    }

    /**
     * Validates dynamic form submission values against the schema definition (ADR-0005 Decision 1).
     *
     * @param schemaJson the form template schema definition
     * @param formData   the user-submitted form field values
     */
    public void validateFormData(List<Map<String, Object>> schemaJson, Map<String, Object> formData) {
        if (schemaJson == null || schemaJson.isEmpty()) {
            return;
        }

        for (Map<String, Object> field : schemaJson) {
            String key = (String) field.get("key");
            if (key == null) {
                continue;
            }
            String type = field.get("type") != null ? field.get("type").toString() : "TEXT";
            boolean required = Boolean.TRUE.equals(field.get("required"));
            Object rawVal = formData != null ? formData.get(key) : null;

            if (required) {
                if (rawVal == null || (rawVal instanceof String s && s.isBlank())) {
                    log.warn("Form validation failed: required field '{}' is missing or blank", key);
                    throw new BusinessException(ErrorCode.WF_FORM_VALIDATION_FAILED);
                }
            }

            if (rawVal != null) {
                if ("NUMBER".equalsIgnoreCase(type)) {
                    BigDecimal bd;
                    try {
                        bd = new BigDecimal(rawVal.toString().trim());
                    } catch (Exception e) {
                        log.warn("Form validation failed: field '{}' value '{}' is not numeric", key, rawVal);
                        throw new BusinessException(ErrorCode.WF_FORM_VALIDATION_FAILED);
                    }
                    if (field.containsKey("min") && field.get("min") != null) {
                        BigDecimal min = new BigDecimal(field.get("min").toString());
                        if (bd.compareTo(min) < 0) {
                            log.warn("Form validation failed: field '{}' value '{}' is below min '{}'", key, bd, min);
                            throw new BusinessException(ErrorCode.WF_FORM_VALIDATION_FAILED);
                        }
                    }
                    if (field.containsKey("max") && field.get("max") != null) {
                        BigDecimal max = new BigDecimal(field.get("max").toString());
                        if (bd.compareTo(max) > 0) {
                            log.warn("Form validation failed: field '{}' value '{}' exceeds max '{}'", key, bd, max);
                            throw new BusinessException(ErrorCode.WF_FORM_VALIDATION_FAILED);
                        }
                    }
                } else if ("SELECT".equalsIgnoreCase(type) && field.containsKey("options")) {
                    Object optionsObj = field.get("options");
                    if (optionsObj instanceof List<?> optionsList && !optionsList.isEmpty()) {
                        boolean matched = false;
                        for (Object opt : optionsList) {
                            if (opt instanceof Map<?, ?> optMap && optMap.containsKey("value")) {
                                if (Objects.equals(String.valueOf(optMap.get("value")), rawVal.toString().trim())) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                        if (!matched) {
                            log.warn("Form validation failed: field '{}' option '{}' not in allowed options", key, rawVal);
                            throw new BusinessException(ErrorCode.WF_FORM_VALIDATION_FAILED);
                        }
                    }
                }
            }
        }
    }

    private FormTemplateResponse toFormTemplateResponse(FormTemplate t) {
        return new FormTemplateResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getCategory(),
                t.getCurrentVersion(),
                t.getSchemaJson(),
                t.isEnabled(),
                t.getDescription(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    // ==========================================
    // FlowDefinition, Nodes & Conditions
    // ==========================================

    @Transactional(readOnly = true)
    public List<FlowDefinitionResponse> listFlowDefinitions() {
        return flowDefinitionRepository.findAllByOrderByIdAsc().stream()
                .map(this::toFlowDefinitionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowDefinitionDetailResponse getFlowDefinitionDetail(Long id) {
        FlowDefinition def = flowDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_FLOW_DEF_NOT_FOUND));

        List<FlowNode> nodes = flowNodeRepository.findByFlowDefinitionIdOrderByStepOrderAsc(def.getId());
        List<Long> nodeIds = nodes.stream().map(FlowNode::getId).toList();
        List<FlowNodeCondition> conditions = nodeIds.isEmpty()
                ? List.of()
                : flowNodeConditionRepository.findByFlowNodeIdInOrderByLogicalGroupAscIdAsc(nodeIds);

        List<FlowNodeResponse> nodeResponses = nodes.stream().map(node -> {
            List<FlowNodeConditionResponse> nodeConds = conditions.stream()
                    .filter(c -> Objects.equals(c.getFlowNodeId(), node.getId()))
                    .map(c -> new FlowNodeConditionResponse(
                            c.getId(),
                            c.getFlowNodeId(),
                            c.getLogicalGroup(),
                            c.getFieldKey(),
                            c.getOperator(),
                            c.getExpectedValue()
                    ))
                    .toList();

            return new FlowNodeResponse(
                    node.getId(),
                    node.getFlowDefinitionId(),
                    node.getStepOrder(),
                    node.getNodeCode(),
                    node.getName(),
                    node.getApproverStrategy(),
                    node.getRoleCode(),
                    node.isSelfApprovalAutoSkip(),
                    node.isAllowDeputy(),
                    nodeConds
            );
        }).toList();

        return new FlowDefinitionDetailResponse(
                def.getId(),
                def.getCode(),
                def.getName(),
                def.getTargetType(),
                def.getFormTemplateCode(),
                def.isEnabled(),
                def.getDescription(),
                nodeResponses,
                def.getCreatedAt(),
                def.getUpdatedAt()
        );
    }

    @Transactional
    public FlowDefinitionDetailResponse updateFlowNodesAndConditions(Long flowDefinitionId, List<FlowNodeUpsertRequest> nodeRequests) {
        FlowDefinition def = flowDefinitionRepository.findById(flowDefinitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_FLOW_DEF_NOT_FOUND));

        List<FlowNode> oldNodes = flowNodeRepository.findByFlowDefinitionIdOrderByStepOrderAsc(flowDefinitionId);
        List<Long> oldNodeIds = oldNodes.stream().map(FlowNode::getId).toList();
        if (!oldNodeIds.isEmpty()) {
            flowNodeConditionRepository.deleteByFlowNodeIdIn(oldNodeIds);
        }
        flowNodeRepository.deleteByFlowDefinitionId(flowDefinitionId);
        flowNodeRepository.flush();

        if (nodeRequests != null) {
            int step = 1;
            for (FlowNodeUpsertRequest req : nodeRequests) {
                FlowNode node = new FlowNode();
                node.setFlowDefinitionId(flowDefinitionId);
                node.setStepOrder(req.stepOrder() > 0 ? req.stepOrder() : step++);
                node.setNodeCode(req.nodeCode());
                node.setName(req.name());
                node.setApproverStrategy(req.approverStrategy());
                node.setRoleCode(req.roleCode());
                node.setSelfApprovalAutoSkip(req.selfApprovalAutoSkip() == null || req.selfApprovalAutoSkip());
                node.setAllowDeputy(req.allowDeputy() == null || req.allowDeputy());

                FlowNode savedNode = flowNodeRepository.save(node);

                if (req.conditions() != null) {
                    for (FlowNodeConditionUpsertRequest cReq : req.conditions()) {
                        FlowNodeCondition cond = new FlowNodeCondition();
                        cond.setFlowNodeId(savedNode.getId());
                        cond.setLogicalGroup(cReq.logicalGroup() > 0 ? cReq.logicalGroup() : 1);
                        cond.setFieldKey(cReq.fieldKey());
                        cond.setOperator(cReq.operator());
                        cond.setExpectedValue(cReq.expectedValue());
                        flowNodeConditionRepository.save(cond);
                    }
                }
            }
        }

        return getFlowDefinitionDetail(flowDefinitionId);
    }

    private FlowDefinitionResponse toFlowDefinitionResponse(FlowDefinition d) {
        return new FlowDefinitionResponse(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getTargetType(),
                d.getFormTemplateCode(),
                d.isEnabled(),
                d.getDescription(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    // ==========================================
    // Deputy Delegation CRUD
    // ==========================================

    @Transactional
    public WorkflowDeputyResponse createDeputy(Long defaultDelegatorUserId, WorkflowDeputyCreateRequest req) {
        Long delegatorUserId = req.delegatorUserId() != null ? req.delegatorUserId() : defaultDelegatorUserId;
        if (Objects.equals(delegatorUserId, req.deputyUserId())) {
            throw new BusinessException(ErrorCode.WF_DEPUTY_SELF_DELEGATION_FORBIDDEN);
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<WorkflowDeputy> existing = workflowDeputyRepository.findByDelegatorUserIdAndEnabledTrue(delegatorUserId);
        for (WorkflowDeputy d : existing) {
            boolean overlaps = !d.getStartDate().isAfter(req.endDate()) && !d.getEndDate().isBefore(req.startDate());
            if (overlaps) {
                throw new BusinessException(ErrorCode.WF_DEPUTY_OVERLAPPING_PERIOD);
            }
        }

        WorkflowDeputy deputy = new WorkflowDeputy();
        deputy.setDelegatorUserId(delegatorUserId);
        deputy.setDeputyUserId(req.deputyUserId());
        deputy.setStartDate(req.startDate());
        deputy.setEndDate(req.endDate());
        deputy.setEnabled(true);
        deputy.setReason(req.reason());

        WorkflowDeputy saved = workflowDeputyRepository.save(deputy);
        return toWorkflowDeputyResponse(saved);
    }

    @Transactional
    public void disableDeputy(Long deputyId, Long actorUserId, boolean isAdmin) {
        WorkflowDeputy deputy = workflowDeputyRepository.findById(deputyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WF_DEPUTY_NOT_FOUND));

        if (!isAdmin && !Objects.equals(deputy.getDelegatorUserId(), actorUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        deputy.setEnabled(false);
        workflowDeputyRepository.save(deputy);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDeputyResponse> listMyDeputies(Long userId) {
        return workflowDeputyRepository.findByDelegatorUserIdOrDeputyUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toWorkflowDeputyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowDeputyResponse> listAllDeputies() {
        return workflowDeputyRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(this::toWorkflowDeputyResponse)
                .toList();
    }

    private WorkflowDeputyResponse toWorkflowDeputyResponse(WorkflowDeputy d) {
        String delegatorName = userRepository.findById(d.getDelegatorUserId())
                .map(User::getDisplayName)
                .orElse("User#" + d.getDelegatorUserId());
        String deputyName = userRepository.findById(d.getDeputyUserId())
                .map(User::getDisplayName)
                .orElse("User#" + d.getDeputyUserId());

        return new WorkflowDeputyResponse(
                d.getId(),
                d.getDelegatorUserId(),
                delegatorName,
                d.getDeputyUserId(),
                deputyName,
                d.getStartDate(),
                d.getEndDate(),
                d.isEnabled(),
                d.getReason(),
                d.getCreatedAt()
        );
    }
}
