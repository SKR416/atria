# Atria Implementation Roadmap

This document tracks the vertical-slice implementation plan for **Atria** (*Attendance & Approval Workflow Platform*). Each completed phase is merged into `main` and tagged with a semantic version.

---

## Phase Overview

| Phase | Git Tag | Focus Area | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton (`atria-backend`), unified `ApiResponse<T>`, trilingual `ErrorCode` i18n | ✅ Completed |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), Spring Security 6 + JWT, RBAC, department tree with `leader_id`, `@DataScope`, `@AuditLog` | ✅ Completed |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 shell (`atria-frontend`), three layouts (`/public`, `/portal`, `/admin`), trilingual switcher, IAM admin UI, plus `iam_user.hire_date` (`V2`) | ✅ Completed |
| **Phase 3a** | `v0.4.0-attendance-core` | Multi-region calendar (`TW`/`JP` templates), immutable `att_punch_log` + derived `att_daily_record`, punch rules & cutoff, bot punch, daily settlement, monthly closing, `/portal` clock-in & calendar, `/admin` calendar UI | ⏳ Next |
| **Phase 3b** | `v0.4.5-leave-overtime` | Leave types & min units, seniority grant rules & job (`hire_date`), leave calculator, balance ledger, overtime rate/cap rules, comp leave conversion & expiry, approval event contract + dev/test approval stub | ⏳ Planned |
| **Phase 4** | `v0.5.0-workflow` | Dynamic form templates (`wf_form_template`), structured-condition approval workflow (`wf_*`), approver fallback, deputy delegation (`wf_deputy`), notification foundation (`ntf_*`) & monthly leave digest | ⏳ Planned |
| **Phase 5** | `v0.6.0-dashboard-notice` | Manager attendance dashboard, anomaly filtering, payroll weighted-hour Excel/CSV export, webhook bot notifications, public (`/public`) & internal (`/portal`) announcements (`ann_*`) | ⏳ Planned |
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
- [x] Flyway migration `V1__init_iam_schema.sql`: `iam_user`, `iam_department`, `iam_role`, `iam_permission`, `iam_audit_log` (PostgreSQL & H2 dialects)
- [x] Department timezone support (`iam_department.timezone` default `Asia/Taipei`) for regional cutoffs
- [x] Account security: 5 consecutive failed login lockout (`locked_until`), DBA emergency plaintext/`{noop}` recovery forcing password change, plus PostgreSQL partial unique index (`WHERE is_deleted = FALSE`) for soft delete
- [x] Field-level encryption converter `AesGcmStringConverter` (`AES-256-GCM` with 96-bit random IV and `{v1}` prefix)
- [x] Stateless JWT authentication (Access Token in memory + Refresh Token in HttpOnly cookie) and BCrypt (strength 12) password hashing
- [x] CRUD APIs for users, hierarchical departments (with `parent_id`, `leader_id`, and cascading `tree_path`), roles, and permissions
- [x] `@DataScope` AOP aspect for filtering manager queries by department subtree
- [x] `@AuditLog` AOP aspect for recording administrative mutations

### Phase 2: Frontend Three-Hall Shell & IAM Console (`v0.3.0-frontend-shell`)
- [x] Scaffold `atria-frontend` with Vue 3, Vite, TypeScript, PrimeVue, Lucide Icons, Pinia, Vue Router, and `vue-i18n` (high-density Japanese B2B layout)
- [x] Axios interceptor with JWT attachment (Pinia memory + HttpOnly cookie refresh), mandatory password-change gate (`1003`), and `Accept-Language` propagation
- [x] Implement three layout shells: `PublicLayout` (`/public`), `PortalLayout` (`/portal`), and `AdminLayout` (`/admin`), plus `/login` and `/change-password`
- [x] Build `/admin/iam/*` management pages for Departments (`TreeTable`), Users (paged `DataTable`, password reset, manual unlock, and `hireDate` `DatePicker`), Roles & Permissions matrix, and Audit Logs
- [x] Flyway migration `V2__add_user_hire_date.sql` (PostgreSQL & H2): add `iam_user.hire_date DATE` across Entity, DTOs, integration tests, and admin UI (`ADR-0008` Decision 11)

### Phase 3a: Attendance Core, Multi-Region Calendar & Employee Portal (`v0.4.0-attendance-core`)
- [ ] Flyway migration `V3__init_attendance_core_schema.sql`: `iam_department.calendar_code`, `att_work_calendar` (`calendar_code`, legal day types `WORKDAY` | `MAKEUP_WORKDAY` | `REST_DAY` | `STATUTORY_REST_DAY` | `NATIONAL_HOLIDAY`), `att_punch_rule`, `att_punch_log` (append-only raw punches), `att_daily_record` (derived daily status & worked minutes), `att_monthly_close`
- [ ] Multi-Region Work Calendar API & Year Generator: built-in `TW` and `JP` statutory holiday templates (generated in Java, editable by HR)
- [ ] Clock-in / Clock-out & Bot Proxy API: write append-only `att_punch_log` (`WEB` | `BOT` | `CORRECTION` with HMAC anti-replay & real client IP) and derive `att_daily_record` with `day_cutoff_hour` (`04:00` AM) and 1-hour lunch break deduction
- [ ] Daily Settlement Job & Monthly Closing Lock (`att_monthly_close`): finalize daily attendance statuses per timezone after cutoff; block monthly close while any `PENDING` request touches that month
- [ ] Build `/portal` Employee Self-Service dashboard (live clock-in/out card + `@fullcalendar/vue3` monthly attendance & calendar view)
- [ ] Build `/admin` HR Work Calendar (with `TW`/`JP` year generator) and Monthly Closing configuration UI

### Phase 3b: Leave Ledger, Statutory Overtime & Approval Event Contract (`v0.4.5-leave-overtime`)
- [ ] Flyway migration `V4__init_leave_overtime_schema.sql`: `att_leave_type` (`min_unit_minutes`), `att_leave_grant_rule`, `att_leave_balance`, `att_overtime_rate_rule`, `att_overtime_cap_rule`, `att_comp_leave_grant`, `att_request`
- [ ] Seniority-based Annual Leave Grant Job: evaluate `iam_user.hire_date` against `TW` (LSA Art. 38) and `JP` (LSA Art. 39, including 5-day obligation tracking) rules
- [ ] Leave Duration Calculator & Balance Ledger: intersect requested intervals with department calendar, deduct 12:00–13:00 break, round up to `min_unit_minutes`, and manage `pending_hours` / `used_hours`
- [ ] Overtime Rate / Cap Engine & Comp-Leave Conversion: evaluate `TW`/`JP` overtime multipliers and monthly/yearly caps (`WARN` / `BLOCK`), support `PAY` vs `COMP_LEAVE` (`HOURS_1_TO_1` or `RATE_MULTIPLIED`), and preserve original band minutes for expiry/termination payout
- [ ] Domain Event Contract (`AttRequestApprovedEvent` / `Rejected` / `Revoked`) + dev/test-only direct approval endpoint for full-loop verification prior to Phase 4
- [ ] Build `/portal` Leave / Punch-Correction / Overtime application UI and `/admin` Leave & Overtime Rules management UI

### Phase 4: Dynamic Forms, Multi-Level Approval Workflow & Notifications (`v0.5.0-workflow`)
- [ ] Flyway migration `V5__init_workflow_and_notification_schema.sql`: `wf_flow_definition`, `wf_flow_node`, `wf_flow_node_condition`, `wf_form_template`, `wf_instance`, `wf_task_log`, `wf_deputy`, plus `ntf_*` notification tables
- [ ] Structured-condition approval evaluator in Java (no SpEL), department tree leader resolution with walk-up and `HR` role fallback, self-approval auto-skip, deputy delegation (`wf_deputy`), Return-to-Draft with UI Diff, revocation, and HR forced reassignment
- [ ] Connect workflow terminal transitions to publish `AttRequestApprovedEvent` / `Rejected` / `Revoked`
- [ ] Notification channels (`InAppNotificationChannel`, `EmailNotificationChannel` with dev log/file sink) and `LeaveBalanceDigestJob` (monthly balance & expiry/JP 5-day obligation digest)
- [ ] Build `/admin` visual workflow rules UI and `/portal` approval inbox / my applications UI

### Phase 5: Manager Dashboard, Payroll Export & Announcements (`v0.6.0-dashboard-notice`)
- [ ] Flyway migration `V6__init_announcement_schema.sql`: `ann_notice`, `ann_read_receipt`
- [ ] Manager Dashboard API & UI (`/admin`): ApexCharts for attendance KPIs, anomaly filtering, and pending approvals
- [ ] HR monthly attendance & weighted overtime summary export API: Excel (`.xlsx` via Apache POI SXSSF) and UTF-8 BOM CSV
- [ ] Outbound `WebhookBotNotificationChannel` (Slack/Teams/LINE bot proxy)
- [ ] Public announcements page (`/public`) and internal announcements feed (`/portal`) with sanitized Tiptap rich text and read receipts

### Phase 6: Practical AI Utilities (`v0.7.0-ai-suite`)
- [ ] Flyway migration `V7__init_ai_schema.sql`: `ai_chat_session`, `ai_chat_message`, `ai_stt_job`, `ai_usage_quota`
- [ ] Unified `AiProvider` interface with a zero-key local mock implementation and standalone `whisper.cpp` HTTP adapter
- [ ] Embedded AI Chat with SSE streaming, workplace text transformation templates, and async audio-to-text transcription (STT)
