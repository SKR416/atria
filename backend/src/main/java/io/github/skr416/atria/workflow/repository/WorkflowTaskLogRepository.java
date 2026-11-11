package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.WorkflowTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTaskLogRepository extends JpaRepository<WorkflowTaskLog, Long> {

    List<WorkflowTaskLog> findByInstanceIdOrderByActedAtAscIdAsc(Long instanceId);
}
