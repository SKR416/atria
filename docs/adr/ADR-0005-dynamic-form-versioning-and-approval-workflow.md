# ADR-0005: Dynamic Form Versioning and Multi-Level Approval Workflow

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Atria supports both built-in attendance requests (`att_request`: leave and punch corrections) and administrator-defined dynamic forms (`wf_form_template`). A common defect in dynamic form systems occurs when an administrator modifies a form's field schema in production, causing historical approval records submitted months earlier to fail rendering or misalign fields.

## Decision

1. **PostgreSQL `JSONB` Schema with Immutable Version Snapshots (`wf_form_template`):**
   - Form field definitions (field keys, component types, validation rules, trilingual labels) are stored in a `schema_json JSONB` column.
   - Every published change increments the template's `version` number.
   - When a workflow instance (`wf_instance`) is submitted, it records both the user's `form_data JSONB` and a snapshot reference to the exact `form_version` (and `schema_snapshot JSONB`) active at submission time.
2. **Explicit Multi-Level Approval State Machine (`wf_instance` & `wf_task_log`):**
   - Workflow instances transition through explicit states: `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`.
   - Flow definitions (`wf_flow_definition`) support step resolution by `DIRECT_LEADER` (resolved via `iam_department.leader_id`), `UPPER_LEADER` (parent department's `leader_id`), or `SPECIFIC_ROLE` (e.g., `HR_ADMIN`), as well as conditional branching (e.g., leave duration > 24 hours requires upper-level approval).
3. **Transactional Attendance Write-Back:**
   - When a workflow instance linked to an `att_request` transitions to `APPROVED`, an application event updates `att_punch_record` within the same transaction (`ON_LEAVE` for leave requests, `NORMAL` with corrected timestamps for punch-correction requests).

## Consequences

- Historical approval forms remain permanently auditable and render accurately regardless of future template edits.
- Using a lightweight domain state machine instead of embedding a heavy BPMN 2.0 engine (such as Camunda or Flowable) keeps memory footprint low and code comprehension high while covering 95% of enterprise approval patterns.
