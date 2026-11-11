package io.github.skr416.atria.workflow.model;

/**
 * Lifecycle states of a workflow instance {@code wf_instance} (ADR-0005 Decision 3).
 */
public enum WorkflowInstanceStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REVOKED
}
