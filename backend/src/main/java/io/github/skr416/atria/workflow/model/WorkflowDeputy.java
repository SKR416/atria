package io.github.skr416.atria.workflow.model;

import io.github.skr416.atria.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * Deputy delegation period entity in {@code wf_deputy} (ADR-0005 Decision 3).
 */
@Getter
@Setter
@Entity
@Table(name = "wf_deputy")
@SQLRestriction("is_deleted = false")
public class WorkflowDeputy extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delegator_user_id", nullable = false)
    private Long delegatorUserId;

    @Column(name = "deputy_user_id", nullable = false)
    private Long deputyUserId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "reason", length = 255)
    private String reason;
}
