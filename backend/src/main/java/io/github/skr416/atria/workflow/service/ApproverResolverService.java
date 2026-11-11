package io.github.skr416.atria.workflow.service;

import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.workflow.model.FlowNode;
import io.github.skr416.atria.workflow.model.WorkflowDeputy;
import io.github.skr416.atria.workflow.repository.WorkflowDeputyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service resolving designated step approvers (DIRECT_LEADER, PARENT_LEADER, SPECIFIC_ROLE)
 * with self-approval auto-skip, multi-level fallbacks (HR_ADMIN -> SUPER_ADMIN), and deputy verification (ADR-0008 Decisions 9 & 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApproverResolverService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final WorkflowDeputyRepository workflowDeputyRepository;

    /**
     * Resolves the designated approver user ID for a flow node.
     *
     * @param node                   the workflow flow node
     * @param applicantId            the applicant user ID
     * @param applicantDepartmentId  the applicant's department ID
     * @param isLastActiveStep       true if this node is the last active step in the flow
     * @return the resolved approver user ID
     */
    @Transactional(readOnly = true)
    public Long resolveApprover(FlowNode node, Long applicantId, Long applicantDepartmentId, boolean isLastActiveStep) {
        if (node == null) {
            throw new BusinessException(ErrorCode.WF_NO_APPROVER_RESOLVED);
        }

        return switch (node.getApproverStrategy()) {
            case DIRECT_LEADER -> {
                User leader = findLeaderWalkingUp(applicantDepartmentId, applicantId, node.isSelfApprovalAutoSkip());
                if (leader == null) {
                    yield fallbackAdmin(applicantId, isLastActiveStep, node.isSelfApprovalAutoSkip());
                }
                if (leader.getId().equals(applicantId) && node.isSelfApprovalAutoSkip()) {
                    if (!isLastActiveStep) {
                        yield applicantId; // Signals engine to AUTO_SKIP
                    } else {
                        yield fallbackAdmin(applicantId, isLastActiveStep, true);
                    }
                }
                yield leader.getId();
            }
            case PARENT_LEADER -> {
                Long parentDeptId = resolveParentDepartmentId(applicantDepartmentId);
                User leader = findLeaderWalkingUp(parentDeptId, applicantId, node.isSelfApprovalAutoSkip());
                if (leader == null) {
                    yield fallbackAdmin(applicantId, isLastActiveStep, node.isSelfApprovalAutoSkip());
                }
                if (leader.getId().equals(applicantId) && node.isSelfApprovalAutoSkip()) {
                    if (!isLastActiveStep) {
                        yield applicantId; // Signals engine to AUTO_SKIP
                    } else {
                        yield fallbackAdmin(applicantId, isLastActiveStep, true);
                    }
                }
                yield leader.getId();
            }
            case SPECIFIC_ROLE -> {
                String roleCode = node.getRoleCode();
                if (roleCode == null || roleCode.isBlank()) {
                    throw new BusinessException(ErrorCode.WF_NO_APPROVER_RESOLVED);
                }
                List<User> roleUsers = userRepository.findActiveUsersByRoleCode(roleCode);
                if (roleUsers.isEmpty()) {
                    yield fallbackAdmin(applicantId, isLastActiveStep, node.isSelfApprovalAutoSkip());
                }
                User candidate = null;
                for (User u : roleUsers) {
                    if (!node.isSelfApprovalAutoSkip() || !u.getId().equals(applicantId)) {
                        candidate = u;
                        break;
                    }
                }
                if (candidate != null) {
                    yield candidate.getId();
                }
                if (!isLastActiveStep) {
                    yield applicantId; // Signals engine to AUTO_SKIP
                }
                yield fallbackAdmin(applicantId, isLastActiveStep, true);
            }
        };
    }

    private Long resolveParentDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        Department dept = departmentRepository.findById(departmentId).orElse(null);
        if (dept != null && dept.getParentId() != null) {
            return dept.getParentId();
        }
        return departmentId;
    }

    private User findLeaderWalkingUp(Long startDeptId, Long applicantId, boolean autoSkip) {
        Long currentDeptId = startDeptId;
        User candidate = null;
        while (currentDeptId != null) {
            Department dept = departmentRepository.findById(currentDeptId).orElse(null);
            if (dept == null) {
                break;
            }
            if (dept.getLeaderId() != null) {
                User leader = userRepository.findById(dept.getLeaderId()).orElse(null);
                if (leader != null && leader.isEnabled()) {
                    if (autoSkip && leader.getId().equals(applicantId)) {
                        candidate = leader;
                    } else {
                        return leader;
                    }
                }
            }
            currentDeptId = dept.getParentId();
        }
        return candidate;
    }

    private Long fallbackAdmin(Long applicantId, boolean isLastActiveStep, boolean autoSkip) {
        List<User> hrAdmins = userRepository.findActiveUsersByRoleCode("HR_ADMIN");
        for (User u : hrAdmins) {
            if (!autoSkip || !u.getId().equals(applicantId)) {
                return u.getId();
            }
        }
        List<User> superAdmins = userRepository.findActiveUsersByRoleCode("SUPER_ADMIN");
        for (User u : superAdmins) {
            if (!autoSkip || !u.getId().equals(applicantId)) {
                return u.getId();
            }
        }
        if (isLastActiveStep) {
            if (!hrAdmins.isEmpty()) {
                return hrAdmins.get(0).getId();
            }
            if (!superAdmins.isEmpty()) {
                return superAdmins.get(0).getId();
            }
        }
        if (!hrAdmins.isEmpty()) {
            return hrAdmins.get(0).getId();
        }
        if (!superAdmins.isEmpty()) {
            return superAdmins.get(0).getId();
        }
        throw new BusinessException(ErrorCode.WF_NO_APPROVER_RESOLVED);
    }

    /**
     * Verifies whether actorUserId is authorized to act on behalf of assignedApproverId
     * either directly or through an active deputy delegation (ADR-0005 Decision 3).
     *
     * @param assignedApproverId the designated approver user ID
     * @param actorUserId        the actual current user ID performing the task
     * @param allowDeputy        whether the step node permits deputy delegation
     * @param today              the effective evaluation date
     * @return Optional containing delegatorUserId if acting as deputy, or empty if direct approver
     */
    @Transactional(readOnly = true)
    public Optional<Long> verifyApproverOrDeputy(Long assignedApproverId, Long actorUserId, boolean allowDeputy, LocalDate today) {
        if (Objects.equals(assignedApproverId, actorUserId)) {
            return Optional.empty();
        }
        if (allowDeputy && assignedApproverId != null && actorUserId != null) {
            LocalDate checkDate = today != null ? today : LocalDate.now();
            List<WorkflowDeputy> deputies = workflowDeputyRepository
                    .findByDelegatorUserIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                            assignedApproverId, checkDate, checkDate
                    );
            for (WorkflowDeputy dep : deputies) {
                if (Objects.equals(dep.getDeputyUserId(), actorUserId)) {
                    return Optional.of(assignedApproverId);
                }
            }
        }
        throw new BusinessException(ErrorCode.WF_UNAUTHORIZED_APPROVER);
    }

    /**
     * Finds all delegator user IDs for whom deputyUserId is currently an active authorized deputy.
     */
    @Transactional(readOnly = true)
    public List<Long> findDelegatorIdsForDeputy(Long deputyUserId, LocalDate today) {
        if (deputyUserId == null) {
            return List.of();
        }
        LocalDate checkDate = today != null ? today : LocalDate.now();
        return workflowDeputyRepository
                .findByDeputyUserIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        deputyUserId, checkDate, checkDate
                )
                .stream()
                .map(WorkflowDeputy::getDelegatorUserId)
                .distinct()
                .toList();
    }
}
