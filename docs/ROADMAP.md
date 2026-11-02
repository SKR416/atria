# Atria Implementation Roadmap

This document tracks the 7-phase vertical-slice implementation plan for **Atria** (*Attendance & Approval Workflow Platform*). Each completed phase is merged into `main` and tagged with a semantic version.

---

## Phase Overview

| Phase | Git Tag | Focus Area | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton (`atria-backend`), unified `ApiResponse<T>`, trilingual `ErrorCode` i18n | ✅ Completed |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), Spring Security 6 + JWT, RBAC, department tree with `leader_id`, `@DataScope`, `@AuditLog` | 🔄 In Progress |
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
- [x] Scaffold `atria-backend` (`io.github.skr416.atria`) with Spring Boot 3, Flyway, PostgreSQL / H2 profiles, and Springdoc OpenAPI
- [x] Implement `ApiResponse<T>`, `PageResult<T>`, `ErrorCode`, `BusinessException`, and `GlobalExceptionHandler`
- [x] Configure trilingual `MessageSource` (`messages_zh_TW.properties`, `messages_en_US.properties`, `messages_ja_JP.properties`) and UTC time handling

### Phase 1: Identity, Department Tree & Access Control (`v0.2.0-iam`)
- [ ] Flyway migration `V1__init_iam_schema.sql`: `iam_user`, `iam_department`, `iam_role`, `iam_permission`, `iam_audit_log`
- [ ] Department timezone support (`iam_department.timezone` default `Asia/Taipei`) for regional cutoffs
- [ ] Account security: 5 consecutive failed login lockout (`locked_until`), plus PostgreSQL partial unique index (`WHERE is_deleted = FALSE`) for soft delete
- [ ] Stateless JWT authentication (Access Token in memory + Refresh Token in HttpOnly cookie) and BCrypt password hashing
- [ ] CRUD APIs for users, hierarchical departments (with `parent_id` and `leader_id`), roles, and permissions
- [ ] `@DataScope` AOP aspect for filtering manager queries by department subtree
- [ ] `@AuditLog` AOP aspect for recording administrative mutations

### Phase 2: Frontend Three-Hall Shell & IAM Console (`v0.3.0-frontend-shell`)
- [ ] Scaffold `atria-frontend` with Vue 3, Vite, TypeScript, PrimeVue, Lucide Icons, Pinia, Vue Router, and `vue-i18n` (high-density Japanese B2B layout)
- [ ] Axios interceptor with JWT attachment (Pinia memory + HttpOnly cookie refresh) and `Accept-Language` propagation
- [ ] Implement three layout shells: `PublicLayout` (`/public`), `PortalLayout` (`/portal`), and `AdminLayout` (`/admin`)
- [ ] Build `/admin` management pages for Users, Departments (tree table), Roles, and Permissions

### Phase 3: Corporate Calendar, Clock-In/Out & Employee Portal (`v0.4.0-attendance-portal`)
- [ ] Flyway migration `V2__init_attendance_schema.sql`: `att_work_calendar`, `att_punch_record`, `att_punch_rule`, `att_leave_type`, `att_leave_balance`, `att_monthly_close`, `att_request`
- [ ] HR Work Calendar API: configure workdays (`WORKDAY`), holidays (`HOLIDAY`), and makeup workdays (`MAKEUP_DAY`)
- [ ] Clock-in / Clock-out API: record dual timestamps (`raw_punch_time` unedited for labor audits, `effective_punch_time` with `att_punch_rule` rounding/grace period and `04:00` AM cross-midnight cutoff), real client IP, and HMAC-verified bot punch proxies
- [ ] Automatic 1-hour fixed lunch break deduction for shifts crossing 12:00–13:00; approval-based overtime request tracking
- [ ] Leave Balance Ledger (`att_leave_balance`): freeze `pending_hours` on submission, deduct on approval, release on reject/revoke, and support HR compensatory leave grants
- [ ] Monthly Closing Lock (`att_monthly_close`) & Scheduled Auto-Close: block any work-hour or request modifications in closed months, and block overdue retroactive applications exceeding `max_retroactive_days`
- [ ] Leave Duration Calculator API: compute net leave hours by intersecting requested intervals with `att_work_calendar`
- [ ] Build `/portal` Employee Self-Service dashboard (live clock-in card + `@fullcalendar/vue3` interactive monthly calendar)
- [ ] Build `/admin` HR Work Calendar, Leave Balance & Monthly Closing configuration UI

### Phase 4: Dynamic Forms & Multi-Level Approval Workflow (`v0.5.0-workflow`)
- [ ] Flyway migration `V3__init_workflow_schema.sql`: `wf_flow_definition`, `wf_flow_node`, `wf_flow_node_condition`, `wf_form_template`, `wf_instance`, `wf_task_log`, `wf_deputy`
- [ ] Table-driven approval rules: Java in-memory evaluator for declarative conditions (e.g. `duration_days > 5` routing to Division Director)
- [ ] Dynamic form schema management with PostgreSQL `JSONB` and immutable version snapshots
- [ ] Multi-level approval state machine with JPA `@Version` optimistic locking, Return-to-Draft (re-start Step 1 with UI Diff), applicant revocation with in-app & webhook notice broadcast, self-approval auto-skip, deputy delegation (`wf_deputy`), and HR forced task reassignment (`REASSIGN`)
- [ ] Automatic transactional write-back listener: approved `att_request` (`LEAVE`, `PUNCH_CORRECTION`, `OVERTIME`) atomically updates `att_punch_record` and `att_leave_balance`
- [ ] Build `/admin` HR visual workflow condition rules management UI

### Phase 5: Manager Dashboard & Dual-Scope Announcements (`v0.6.0-dashboard-notice`)
- [ ] Flyway migration `V4__init_announcement_schema.sql`: `ann_notice`, `ann_read_receipt`
- [ ] Manager Dashboard API & UI (`/admin`): ApexCharts for attendance KPIs (radial attendance rate, monthly hour distribution, attendance anomaly heatmap), recent punch logs, and pending approvals
- [ ] HR monthly attendance summary export API: Excel (`.xlsx` via Apache POI SXSSF streaming) and CSV with UTF-8 BOM (`﻿`)
- [ ] Multi-channel notifications: `InAppNotificationChannel`, `EmailNotificationChannel`, and outbound `WebhookBotNotificationChannel` (Slack/Teams/LINE bot proxy)
- [ ] Public announcements page (`/public`) and internal announcements feed (`/portal`) with sanitized Tiptap rich text and read receipts

### Phase 6: Practical AI Utilities (`v0.7.0-ai-suite`)
- [ ] Flyway migration `V5__init_ai_schema.sql`: `ai_chat_session`, `ai_chat_message`, `ai_stt_job`, `ai_usage_quota`
- [ ] Unified `AiProvider` interface with a zero-key local mock implementation and standalone `whisper.cpp` HTTP adapter
- [ ] Embedded AI Chat with SSE streaming, workplace text transformation templates, and async audio-to-text transcription (STT)
- [ ] Embedded AI Chat with SSE streaming, workplace text transformation templates, and async audio-to-text transcription (STT)
