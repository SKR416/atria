# ADR-0005: Dynamic Form Versioning and Multi-Level Approval Workflow

- **Status:** Accepted (refined by [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md))
- **Date:** 2026-10-28

## Context

Atria supports both built-in attendance requests (`att_request`: leave and punch corrections) and administrator-defined dynamic forms (`wf_form_template`). A common defect in dynamic form systems occurs when an administrator modifies a form's field schema in production, causing historical approval records submitted months earlier to fail rendering or misalign fields.

## Decision

1. **PostgreSQL `JSONB` Schema with Immutable Version Snapshots (`wf_form_template`):**
   - Form field definitions (field keys, component types, validation rules, trilingual labels) are stored in a `schema_json JSONB` column.
   - Every published change increments the template's `version` number.
   - When a workflow instance (`wf_instance`) is submitted, it records both the user's `form_data JSONB` and a snapshot reference to the exact `form_version` (and `schema_snapshot JSONB`) active at submission time.
2. **Table-Driven Approval Flow & Condition Rules (`wf_flow_definition`, `wf_flow_node`, `wf_flow_node_condition`):**
   - Approval routing is fully data-driven via relational database tables rather than hardcoded logic or DB triggers:
     - `wf_flow_definition`: Defines flow code, form type, and trilingual name.
     - `wf_flow_node`: Configures ordered steps (`step_order`), approver strategy (`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`), self-approval auto-skip flag, and deputy allowance flag.
     - `wf_flow_node_condition`: Defines dynamic activation criteria evaluated by the Java workflow engine (e.g., `duration_days > 5` activates an extra `DIVISION_DIRECTOR` step before HR archiving).
   - HR administrators can configure threshold rules through a dedicated UI card interface without modifying application code or restarting services.
3. **Explicit Workflow State Machine & Lifecycle Operations (`wf_instance` & `wf_task_log`):**
   - States: `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`.
   - **Return to Draft (Option A - 差戻し):** Approver can return a pending form back to `DRAFT`. When the applicant modifies and resubmits, the workflow re-starts from Step 1, providing an audit Diff highlighting changes from the previous submission.
   - **Applicant Revocation (取り下げ):** Applicants can revoke any form before final approval. Revocation transitions the form to `DRAFT` and broadcasts an in-app notice to all approvers who previously approved earlier steps.
   - **Self-Approval Avoidance:** If the applicant is the leader of the current approval node, the engine automatically skips the step to the parent department's leader.
   - **Deputy Delegation (`wf_deputy`):** Delegated approvals log both the operator and the delegator IDs for strict compliance auditing.
   - **Optimistic Locking (`@Version`) & HR Force Reassign:** `wf_instance` and `att_leave_balance` enforce JPA `@Version` optimistic locking to prevent concurrent double-approvals by a leader and deputy. HR administrators can forcibly reassign pending tasks (`REASSIGN`) when an approver leaves or is unavailable, with full audit logging in `wf_task_log`.
4. **Transactional Attendance Write-Back (refined by [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md)):**
   - When a workflow instance linked to an `att_request` transitions to a terminal or revoked state, it publishes domain events (`AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent`) within the same database transaction:
     - For `LEAVE`: commits frozen `pending_hours` into `used_hours` in `att_leave_balance` and updates `att_daily_record` (`ON_LEAVE`).
     - For `PUNCH_CORRECTION`: appends an immutable `CORRECTION` row to `att_punch_log` and recomputes `att_daily_record`.
     - For `OVERTIME`: records approved weighted overtime minutes or grants compensatory leave (`att_comp_leave_grant`).

## Consequences

- Historical approval forms remain permanently auditable and render accurately regardless of future template edits.
- Using a lightweight domain state machine instead of embedding a heavy BPMN 2.0 engine (such as Camunda or Flowable) keeps memory footprint low and code comprehension high while covering 95% of enterprise approval patterns.
