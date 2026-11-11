# Atria Implementation Roadmap

This document tracks the vertical-slice implementation plan for **Atria** (*Attendance & Approval Workflow Platform*). Each completed phase is merged into `main` and tagged with a semantic version.

---

## Phase Overview

| Phase | Git Tag | Focus Area | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton (`atria-backend`), unified `ApiResponse<T>`, trilingual `ErrorCode` i18n | ✅ Completed |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), Spring Security 6 + JWT, RBAC, department tree with `leader_id`, `@DataScope`, `@AuditLog` | ✅ Completed |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 shell (`atria-frontend`), three layouts (`/public`, `/portal`, `/admin`), trilingual switcher, IAM admin UI, plus `iam_user.hire_date` (`V2`) | ✅ Completed |
| **Phase 3a** | `v0.4.0-attendance-core` | Multi-region calendar (`TW`/`JP` templates), immutable `att_punch_log` + derived `att_daily_record`, punch rules & cutoff, bot punch, daily settlement, monthly closing, `/portal` clock-in & calendar, `/admin` calendar UI | ✅ Completed |
| **Phase 3b** | `v0.4.5-leave-overtime` | Leave types & min units, seniority grant rules & job (`hire_date`), leave calculator, balance ledger, overtime rate/cap rules, comp leave conversion & expiry, approval event contract + dev/test approval stub | ✅ Completed |
| **Phase 4** | `v0.5.0-workflow` | Dynamic form templates (`wf_form_template`), structured-condition approval workflow (`wf_*`), approver fallback, deputy delegation (`wf_deputy`), notification foundation (`ntf_*`) & monthly leave digest | ✅ Completed |
| **Phase 5** | `v1.0.0` | Public Hall corporate announcements (`ann_*`), manager dashboard & payroll export, async AI voice-to-text (`ai_stt_job`), and final release polish | ⏳ Next |

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
- [x] Flyway migration `V3__init_attendance_core_schema.sql`: `iam_department.calendar_code`, `att_work_calendar` (`calendar_code`, legal day types `WORKDAY` | `MAKEUP_WORKDAY` | `REST_DAY` | `STATUTORY_REST_DAY` | `NATIONAL_HOLIDAY`), `att_punch_rule`, `att_punch_log` (append-only raw punches), `att_daily_record` (derived daily status & worked minutes), `att_monthly_close`
- [x] Multi-Region Work Calendar API & Year Generator: built-in `TW` and `JP` statutory holiday templates (generated in Java, editable by HR)
- [x] Clock-in / Clock-out & Bot Proxy API: write append-only `att_punch_log` (`WEB` | `BOT` | `CORRECTION` with HMAC anti-replay & real client IP) and derive `att_daily_record` with `day_cutoff_hour` (`04:00` AM) and 1-hour lunch break deduction
- [x] Daily Settlement Job & Monthly Closing Lock (`att_monthly_close`): finalize daily attendance statuses per timezone after cutoff; block monthly close while any `PENDING` request touches that month
- [x] Build `/portal` Employee Self-Service dashboard (live clock-in/out card + interactive 7-column monthly attendance & calendar view)
- [x] Build `/admin` HR Work Calendar (with `TW`/`JP` year generator) and Monthly Closing configuration UI
- [x] Enforce dual-layer `att_punch_log` immutability (PostgreSQL `BEFORE UPDATE OR DELETE` trigger `trg_att_punch_log_immutable` + JPA `@Immutable`), ArchUnit modular boundary suite (`ModularArchitectureTest`), frontend bug-catching ESLint, and GitHub Actions CI (`.github/workflows/ci.yml` with PostgreSQL 16 service)

### Phase 3b: Leave Ledger, Statutory Overtime & Approval Event Contract (`v0.4.5-leave-overtime`)
- [x] Flyway migration `V4__init_leave_overtime_schema.sql`: `att_leave_type` (`min_unit_minutes`), `att_leave_grant_rule` (`ANNIVERSARY` / `CALENDAR_YEAR` basis), `att_leave_balance` (`source_comp_grant_id`), `att_leave_balance_tx` (FIFO per-bucket audit trail), `att_overtime_rate_rule` (TW practical `1.34` / `1.67` / `2.67` / `2.00` & JP statutory templates), `att_overtime_cap_rule`, `att_comp_leave_grant` (with `band_breakdown_json`), `att_request` (`PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `REVOKED` state machine), plus a foreign key on the existing `att_punch_log.correction_request_id`
- [x] Seniority-based Annual Leave Grant Job: evaluate `iam_user.hire_date` on **Anniversary Basis (`ANNIVERSARY`)** against `TW` (LSA Art. 38) and `JP` (LSA Art. 39, including 5-day obligation tracking) rules
- [x] Leave Duration Calculator & FIFO Balance Ledger: intersect requested intervals with department calendar, deduct 12:00–13:00 break, round up to `min_unit_minutes`, deduct across `att_leave_balance` buckets by `valid_until ASC` (FIFO), and record exact bucket deltas in `att_leave_balance_tx` for 100% accurate rejection/revocation restoration
- [x] Overtime Rate / Cap Engine & Comp-Leave Conversion: evaluate `TW` (`1.34` / `1.67` / `2.67` / `2.00`) & `JP` (`1.25` / `1.35`; the `1.50` > 60 h/month band and `0.25` night premium are seeded but evaluated in a later phase) multipliers and monthly/yearly caps (`WARN` / `BLOCK`), support `PAY` vs `COMP_LEAVE` (`HOURS_1_TO_1` or `RATE_MULTIPLIED` with `BigDecimal` + `RoundingMode.CEILING` to whole minutes), and preserve `band_breakdown_json` for expiry/termination payout restoration
- [x] Unidirectional Domain Event Contract in `attendance.event` (`AttRequestSubmittedEvent` / `AttRequestCancelledEvent` / `AttRequestApprovedEvent` / `Rejected` / `Revoked`, keeping `workflow` ➔ `attendance` cycle-free) + `AttendanceRecordCalculator.recomputeDailyRecord` integration (approved `LEAVE` shifts lateness/early thresholds and aggregates `leaveMinutes`; `OVERTIME` aggregates `overtimeMinutes`; `PUNCH_CORRECTION` appends `att_punch_log(CORRECTION)` and recomputes) + dev/test-only direct approval stub
- [x] Leave Balance, 90-Day Expiring Comp-Leave & JP 5-Day Obligation Dashboard API + `/portal` Leave / Punch-Correction / Overtime application UI and `/admin` Leave & Overtime Rules management UI (actual email digest job `LeaveBalanceDigestJob` deferred to Phase 4 once `ntf_*` lands)

### Phase 4: Dynamic Forms, Multi-Level Approval Workflow & Notifications (`v0.5.0-workflow`)
- [x] Flyway migration `V5__init_workflow_and_notification_schema.sql` (PostgreSQL 16 & H2 dialects): 9 new tables (`wf_form_template`, `wf_flow_definition`, `wf_flow_node`, `wf_flow_node_condition`, `wf_instance`, `wf_task_log`, `wf_deputy`, `ntf_notification`, `ntf_digest_log`) + permissions (`wf:instance:self`, `wf:template:read`, `wf:template:write`, `wf:instance:reassign`, `ntf:digest:trigger`)
- [x] Dynamic Form Schema & Immutable Versioning (`wf_form_template`, `wf_instance`): JSON Schema field definitions (`TEXT`, `TEXTAREA`, `NUMBER`, `SELECT`, `DATE`), automatic version bumping on template schema updates (`v1` -> `v2`), and immutable `form_version` + `schema_snapshot_json` preserved on every submitted `wf_instance`
- [x] SpEL-Free Structured Condition Multi-Level Approval Engine (`StructuredConditionEvaluator`, `ApproverResolverService`, `WorkflowEngineService`): pure Java evaluation of `wf_flow_node_condition` (`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, same `logical_group` = AND, across groups = OR), department tree walk-up (`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`) with `HR_ADMIN` -> `SUPER_ADMIN` fallback, and `selfApprovalAutoSkip`
- [x] Bidirectional Attendance Event Loop (`attendance.event.*`): `AttRequestSubmittedEvent` / `AttRequestCancelledEvent` auto-create/cancel linked `wf_instance`; workflow approval/rejection/revocation publishes `AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent` (with pre-revoke validation via `AttRequestGuard`)
- [x] Return-to-Draft (`差戻し`) & Field-by-Field Revision Diff (`diff_json`): supported on standalone dynamic forms (`target_type = 'DYNAMIC_FORM'`), blocked on attendance-linked requests (`3009` `WF_RETURN_TO_DRAFT_NOT_ALLOWED_FOR_ATT_REQUEST` to protect FIFO frozen leave buckets)
- [x] Deputy Delegation (`wf_deputy`) & HR Force Reassign (`REASSIGN`): date-bounded deputy rules with self-delegation (`3012`) and overlap (`3013`) guards, dual audit logging (`actor_user_id` + `delegator_user_id` in `wf_task_log`), and admin forced task reassignment
- [x] In-App Notifications & Idempotent Monthly Leave Digest (`notification` module): `NotificationBell.vue` in Portal & Admin topbars, dev email sink (`EmailNotificationChannel`), and `LeaveBalanceDigestJob` (`ntf_digest_log` with `UNIQUE (user_id, period_year_month)` idempotency)
- [x] Frontend Views & Quality Verification Gate: `NotificationBell.vue` (topbar bell & notification drawer), `PortalWorkflowPanel.vue` (mounted in `/portal`), `WorkflowManageView.vue` (`/admin/workflow/manage`), and zero-browser static UI/i18n gate `frontend/scripts/check-ui-i18n.mjs` verifying 485 trilingual keys across `zh-TW.ts`, `en-US.ts`, and `ja-JP.ts`

### Phase 5: Public Hall Corporate Announcements, Async AI Voice-to-Text (STT) & Final Release Polish (`v1.0.0`)
- [ ] Flyway migration `V6__init_announcement_and_ai_schema.sql`: `ann_notice`, `ann_read_receipt`, `ai_stt_job`, `ai_chat_session`, `ai_usage_quota`
- [ ] Manager Dashboard API & UI (`/admin`): ApexCharts for attendance KPIs, anomaly filtering, and pending approvals
- [ ] HR monthly attendance & weighted overtime summary export API: Excel (`.xlsx` via Apache POI SXSSF) and UTF-8 BOM CSV
- [ ] Outbound `WebhookBotNotificationChannel` (Slack/Teams/LINE bot proxy)
- [ ] Public announcements page (`/public`) and internal announcements feed (`/portal`) with sanitized Tiptap rich text and read receipts
- [ ] Asynchronous audio-to-text transcription (`ai_stt_job`) with standalone `whisper.cpp` HTTP adapter, SSE workplace text transform, and zero-key mock fallback
- [ ] Production build packaging polish, trilingual release verification, and final v1.0.0 milestone tag
