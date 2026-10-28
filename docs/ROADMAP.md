# Atria Implementation Roadmap

This document tracks the 7-phase vertical-slice implementation plan for **Atria** (*Attendance & Approval Workflow Platform*). Each completed phase is merged into `main` and tagged with a semantic version.

---

## Phase Overview

| Phase | Git Tag | Focus Area | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton (`atria-backend`), unified `ApiResponse<T>`, trilingual `ErrorCode` i18n | 🔄 In Progress |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), Spring Security 6 + JWT, RBAC, department tree with `leader_id`, `@DataScope`, `@AuditLog` | ⏳ Planned |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 shell (`atria-frontend`), three layouts (`/public`, `/portal`, `/admin`), trilingual switcher, IAM admin UI | ⏳ Planned |
| **Phase 3** | `v0.4.0-attendance-portal` | HR corporate calendar (`att_work_calendar`), clock-in/out punch (`att_punch_record`), leave calculator, employee portal (`/portal`) | ⏳ Planned |
| **Phase 4** | `v0.5.0-workflow` | Dynamic form templates (`wf_form_template`), multi-level approval workflow (`wf_*`), deputy delegation (`wf_deputy`), attendance write-back | ⏳ Planned |
| **Phase 5** | `v0.6.0-dashboard-notice` | Manager attendance dashboard, anomaly filtering, public (`/public`) & internal (`/portal`) announcements (`ann_*`) | ⏳ Planned |
| **Phase 6** | `v0.7.0-ai-suite` | Embedded AI chat (SSE), workplace text transformation, and asynchronous audio-to-text transcription (`ai_stt_job`) | ⏳ Planned |

---

## Detailed Milestones

### Phase 0: Foundation & Architecture (`v0.1.0-foundation`)
- [x] Initialize Monorepo directory structure (`docs/`, `backend/`, `frontend/`) and `.gitignore`
- [x] Author trilingual READMEs (`README.md`, `README.zh-TW.md`, `README.ja.md`) and English ADRs (`ADR-0001` to `ADR-0007`)
- [ ] Scaffold `atria-backend` (`io.github.skr416.atria`) with Spring Boot 3, Flyway, PostgreSQL / H2 profiles, and Springdoc OpenAPI
- [ ] Implement `ApiResponse<T>`, `PageResult<T>`, `ErrorCode`, `BusinessException`, and `GlobalExceptionHandler`
- [ ] Configure trilingual `MessageSource` (`messages_zh_TW.properties`, `messages_en_US.properties`, `messages_ja_JP.properties`) and UTC time handling

### Phase 1: Identity, Department Tree & Access Control (`v0.2.0-iam`)
- [ ] Flyway migration `V1__init_iam_schema.sql`: `iam_user`, `iam_department`, `iam_role`, `iam_permission`, `iam_audit_log`
- [ ] Stateless JWT authentication (Access Token + Refresh Token) and BCrypt password hashing
- [ ] CRUD APIs for users, hierarchical departments (with `parent_id` and `leader_id`), roles, and permissions
- [ ] `@DataScope` AOP aspect for filtering manager queries by department subtree
- [ ] `@AuditLog` AOP aspect for recording administrative mutations

### Phase 2: Frontend Three-Hall Shell & IAM Console (`v0.3.0-frontend-shell`)
- [ ] Scaffold `atria-frontend` with Vue 3, Vite, TypeScript, Element Plus, Pinia, Vue Router, and `vue-i18n`
- [ ] Axios interceptor with JWT attachment, automatic token refresh, and `Accept-Language` propagation
- [ ] Implement three layout shells: `PublicLayout` (`/public`), `PortalLayout` (`/portal`), and `AdminLayout` (`/admin`)
- [ ] Build `/admin` management pages for Users, Departments, Roles, and Permissions

### Phase 3: Corporate Calendar, Clock-In/Out & Employee Portal (`v0.4.0-attendance-portal`)
- [ ] Flyway migration `V2__init_attendance_schema.sql`: `att_work_calendar`, `att_punch_record`, `att_leave_type`, `att_request`
- [ ] HR Work Calendar API: configure workdays (`WORKDAY`), holidays (`HOLIDAY`), and makeup workdays (`MAKEUP_DAY`)
- [ ] Clock-in / Clock-out API: record server timestamps and evaluate daily status (`NORMAL`, `LATE`, `EARLY_LEAVE`)
- [ ] Leave Duration Calculator API: compute net leave hours by intersecting requested intervals with `att_work_calendar`
- [ ] Build `/portal` Employee Self-Service dashboard (live clock-in card + interactive monthly attendance calendar)
- [ ] Build `/admin` HR Work Calendar configuration UI

### Phase 4: Dynamic Forms & Multi-Level Approval Workflow (`v0.5.0-workflow`)
- [ ] Flyway migration `V3__init_workflow_schema.sql`: `wf_form_template`, `wf_flow_definition`, `wf_instance`, `wf_task_log`, `wf_deputy`
- [ ] Dynamic form schema management with PostgreSQL `JSONB` and immutable version snapshots
- [ ] Multi-level approval state machine (direct department leader -> upper department leader -> HR archive) with deputy support (`wf_deputy`)
- [ ] Automatic write-back listener: approved `att_request` (`LEAVE` or `PUNCH_CORRECTION`) updates `att_punch_record` status to `ON_LEAVE` or `NORMAL`

### Phase 5: Manager Dashboard & Dual-Scope Announcements (`v0.6.0-dashboard-notice`)
- [ ] Flyway migration `V4__init_announcement_schema.sql`: `ann_notice`, `ann_read_receipt`
- [ ] Manager Dashboard API & UI (`/admin`): today's attendance KPIs (expected, present, late, on leave, missing punch), recent punch log table, and pending approvals
- [ ] Public announcements page (`/public`) and internal announcements feed (`/portal`) with read receipts

### Phase 6: Practical AI Utilities (`v0.7.0-ai-suite`)
- [ ] Flyway migration `V5__init_ai_schema.sql`: `ai_chat_session`, `ai_chat_message`, `ai_stt_job`, `ai_usage_quota`
- [ ] Unified `AiProvider` interface with a zero-key local mock implementation and pluggable LLM/STT adapters
- [ ] Embedded AI Chat with SSE streaming, workplace text transformation templates, and async audio-to-text transcription (STT)
