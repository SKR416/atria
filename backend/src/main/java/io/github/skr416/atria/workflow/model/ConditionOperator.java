package io.github.skr416.atria.workflow.model;

/**
 * Structured comparison operators for workflow node activation conditions (ADR-0008 Decision 10).
 * Evaluated in pure Java without SpEL.
 */
public enum ConditionOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN
}
