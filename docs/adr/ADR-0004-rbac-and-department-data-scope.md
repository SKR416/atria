# ADR-0004: RBAC Combined with Department Tree Data Scope

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Pure Role-Based Access Control (RBAC: `User -> Role -> Permission`) determines *which features* a user can access (e.g., viewing the attendance dashboard or approving requests), but cannot express *whose data* a manager is allowed to see or approve. In an enterprise setting:
- A department manager should only see attendance records and approve requests for employees within their own department (and optionally sub-departments).
- HR and system administrators need company-wide visibility.
- When a manager is on leave, their approval authority must be temporarily delegable to a deputy without granting permanent role elevation.

## Decision

1. **Hierarchical Department Tree (`iam_department`):**
   - Each department defines `parent_id`, `tree_path` (materialized path such as `/1/4/12/`), and `leader_id` (the department manager).
2. **Role Data Scope (`DataScopeType`):**
   - Each role in `iam_role` is assigned a `data_scope` level:
     - `ALL`: Full company-wide data access (e.g., HR, Super Admin).
     - `DEPT_AND_SUB`: Access to the user's department and all descendant departments.
     - `DEPT_ONLY`: Access restricted to the user's immediate department.
     - `SELF_ONLY`: Access restricted to the user's own records.
   - A custom `@DataScope` AOP aspect dynamically appends department/user predicates to attendance and workflow queries.
3. **Time-Bounded Approval Deputy (`wf_deputy`):**
   - Managers can configure a deputy (`principal_user_id`, `deputy_user_id`, `start_at`, `end_at`, `active`) in the workflow module so approval routing never stalls during manager absences.

## Consequences

- The same `/admin` attendance dashboard and approval endpoints serve both department managers and HR without duplicating controllers—each caller automatically sees only the rows permitted by their `data_scope`.
- Materialized `tree_path` allows fast subtree filtering (`LIKE '/1/4/%'`) without requiring recursive SQL on every read query.
