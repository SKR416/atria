package io.github.skr416.atria.workflow.model;

/**
 * Target business types routed through the approval workflow engine (ADR-0005 Decision 2).
 */
public enum WorkflowTargetType {
    ATT_LEAVE,
    ATT_OVERTIME,
    ATT_PUNCH_CORRECTION,
    DYNAMIC_FORM
}
