package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.WorkflowInstance;
import io.github.skr416.atria.workflow.model.WorkflowInstanceStatus;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long>, JpaSpecificationExecutor<WorkflowInstance> {

    Optional<WorkflowInstance> findByInstanceNo(String instanceNo);

    Optional<WorkflowInstance> findByAttRequestId(Long attRequestId);

    List<WorkflowInstance> findByApplicantIdOrderByCreatedAtDescIdDesc(Long applicantId);

    List<WorkflowInstance> findByStatusAndCurrentApproverIdOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus status, Long approverId);

    List<WorkflowInstance> findByStatusAndCurrentApproverIdInOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus status, Collection<Long> approverIds);

    List<WorkflowInstance> findByStatusOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus status);

    List<WorkflowInstance> findByTargetTypeOrderByCreatedAtDescIdDesc(WorkflowTargetType targetType);

    List<WorkflowInstance> findByStatusAndTargetTypeOrderByCreatedAtDescIdDesc(WorkflowInstanceStatus status, WorkflowTargetType targetType);

    List<WorkflowInstance> findAllByOrderByCreatedAtDescIdDesc();
}
