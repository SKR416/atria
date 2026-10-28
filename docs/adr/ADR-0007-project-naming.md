# ADR-0007: Project Naming ("Atria") and Schema Prefix Alignment

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

During initial brainstorming, placeholder names such as `Enterprise Base & ESS Portal` and `com.enterprise.base` were used, and several database table prefixes had drifted out of sync with their owning domain modules:
- The Identity & Access Management module was named `iam`, but its tables used the `sys_` prefix (`sys_user`, `sys_department`, `sys_role`, `sys_permission`, `sys_audit_log`).
- Approval delegation (`sys_role_deputy`) was placed under `sys_` even though it governs approval routing in the `workflow` module.
- `att_leave_request` stored both leave applications and missed-punch corrections, making `leave` in the table name inaccurate.

We also needed a memorable, collision-free project name that reflects the architecture without generic buzzwords.

## Alternatives Considered

- **`enterprise-starter` / `business-admin`:** Too generic; hundreds of boilerplate repositories share these names on GitHub, and they do not convey the core domain (attendance + approval workflow).
- **`kintai-flow`:** Mixes Japanese romaji with English; less natural for trilingual documentation.
- **`atrium` (singular):** Conveys a central hall, but does not capture the three distinct entry points (`/public`, `/portal`, `/admin`).

## Decision

1. **Project Name — `Atria`:**
   - **Name:** `Atria` (repository: `atria`)
   - **Subtitle:** *Attendance & Approval Workflow Platform*
   - **Architectural Metaphor:** *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*
   - **Coordinates:**
     - Java package / Maven `groupId`: `io.github.skr416.atria`
     - Maven `artifactId`: `atria-backend`
     - Frontend npm package name: `atria-frontend`
   - **Collision Check:** Verified that `SKR416/atria` is unused on GitHub.

2. **Strict Module-to-Table Prefix Alignment:**
   - Rename all `sys_*` tables to `iam_*` (`iam_user`, `iam_department`, `iam_role`, `iam_permission`, `iam_audit_log`) to match the `iam` module.
   - Move and rename `sys_role_deputy` to `wf_deputy` under the `workflow` (`wf_`) module.
   - Rename `att_leave_request` to `att_request` with a `request_type` column (`LEAVE` | `PUNCH_CORRECTION`).

3. **Documentation & Wording Discipline:**
   - Maintain trilingual root READMEs (`README.md`, `README.zh-TW.md`, `README.ja.md`) and English ADRs (`docs/adr/`).
   - Describe the system accurately as a **modular monolith** (never "cloud-native" or "AI-Agentic").
   - READMEs must always state the current phase/tag and describe only what is implemented.

## Consequences

- Every Java module (`iam`, `attendance`, `workflow`, `announcement`, `ai`) now maps predictably to its SQL table prefix (`iam_`, `att_`, `wf_`, `ann_`, `ai_`).
- The name **Atria** provides a clear, memorable narrative in documentation and technical interviews.
