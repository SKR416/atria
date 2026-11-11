package io.github.skr416.atria.workflow.model;

/**
 * Audit action types recorded in {@code wf_task_log.action} (ADR-0005 Decision 3).
 */
public enum WorkflowTaskAction {
    SUBMIT,
    RESUBMIT,
    APPROVE,
    REJECT,
    RETURN_TO_DRAFT,
    CANCEL,
    REVOKE,
    REASSIGN,
    AUTO_SKIP
}
