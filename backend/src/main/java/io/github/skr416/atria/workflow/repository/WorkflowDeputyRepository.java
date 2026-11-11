package io.github.skr416.atria.workflow.repository;

import io.github.skr416.atria.workflow.model.WorkflowDeputy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface WorkflowDeputyRepository extends JpaRepository<WorkflowDeputy, Long> {

    List<WorkflowDeputy> findByDelegatorUserIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long delegatorUserId, LocalDate now1, LocalDate now2
    );

    List<WorkflowDeputy> findByDeputyUserIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long deputyUserId, LocalDate now1, LocalDate now2
    );

    @Query("select d from WorkflowDeputy d where (d.delegatorUserId = :userId or d.deputyUserId = :userId) order by d.createdAt desc, d.id desc")
    List<WorkflowDeputy> findByDelegatorUserIdOrDeputyUserIdOrderByCreatedAtDesc(Long userId);

    List<WorkflowDeputy> findAllByOrderByCreatedAtDescIdDesc();

    List<WorkflowDeputy> findByDelegatorUserIdAndEnabledTrue(Long delegatorUserId);
}
