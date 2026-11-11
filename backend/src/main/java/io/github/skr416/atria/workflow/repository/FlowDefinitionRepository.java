package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.FlowDefinition;
import io.github.skr416.atria.workflow.model.WorkflowTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowDefinitionRepository extends JpaRepository<FlowDefinition, Long> {

    Optional<FlowDefinition> findByCode(String code);

    boolean existsByCode(String code);

    Optional<FlowDefinition> findByTargetTypeAndEnabledTrue(WorkflowTargetType targetType);

    Optional<FlowDefinition> findByTargetTypeAndFormTemplateCodeAndEnabledTrue(WorkflowTargetType targetType, String formTemplateCode);

    List<FlowDefinition> findAllByOrderByIdAsc();
}
