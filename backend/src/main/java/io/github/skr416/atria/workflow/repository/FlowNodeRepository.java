package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.FlowNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlowNodeRepository extends JpaRepository<FlowNode, Long> {

    List<FlowNode> findByFlowDefinitionIdOrderByStepOrderAsc(Long flowDefinitionId);

    void deleteByFlowDefinitionId(Long flowDefinitionId);
}
