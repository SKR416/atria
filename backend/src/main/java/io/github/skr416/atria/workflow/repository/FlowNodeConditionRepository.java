package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.FlowNodeCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FlowNodeConditionRepository extends JpaRepository<FlowNodeCondition, Long> {

    List<FlowNodeCondition> findByFlowNodeIdOrderByLogicalGroupAscIdAsc(Long flowNodeId);

    List<FlowNodeCondition> findByFlowNodeIdInOrderByLogicalGroupAscIdAsc(Collection<Long> flowNodeIds);

    void deleteByFlowNodeId(Long flowNodeId);

    void deleteByFlowNodeIdIn(Collection<Long> flowNodeIds);
}
