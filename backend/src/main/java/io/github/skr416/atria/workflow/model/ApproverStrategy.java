package io.github.skr416.atria.workflow.model;

/**
 * Approver resolution strategy for a workflow node {@code wf_flow_node} (ADR-0005 Decision 2).
 */
public enum ApproverStrategy {
    DIRECT_LEADER,
    PARENT_LEADER,
    SPECIFIC_ROLE
}
