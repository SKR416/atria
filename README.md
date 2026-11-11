# Atria — Attendance & Approval Workflow Platform

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **Current Status:** `Phase 4 — Dynamic Form & Multi-Level Approval Workflow + In-App Notifications & Leave Digest (Completed, v0.5.0-workflow)`  
> **Next Milestone:** `Phase 5 — Public Hall Corporate Announcements, Async AI Voice-to-Text (STT), & Final Release Polish (v1.0.0)`  
> Phases 0–4 are fully implemented and verified (40 backend tests including 8 ArchUnit modular fitness rules & DB immutability tests + frontend ESLint 9, static UI/i18n check script verifying 485 keys, TypeScript & Vite build). See [docs/ROADMAP.md](./docs/ROADMAP.md) and [docs/NEXT_STEPS.md](./docs/NEXT_STEPS.md) for the full milestone schedule.

Atria is an enterprise platform that brings attendance tracking and approval workflows together. Employees clock in and submit leave, punch-correction, or overtime requests, managers and HR approve them through a multi-level workflow, and approved requests are written back to attendance records automatically. Built as a Spring Boot 3.3 + Vue 3.5 modular monolith with RBAC, department-subtree data scope, dynamic forms, and trilingual i18n (`zh-TW` / `en-US` / `ja-JP`).

---

## Why "Atria"?

*Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

- **`/public` (Public Site):** Unauthenticated external announcements with category filtering, pinned notices, and trilingual switching.
- **`/portal` (Employee Portal / ESS):** Daily workspace for all authenticated employees to clock in/out, view the corporate work calendar, submit leave/punch-correction/overtime requests, read internal announcements, and use lightweight AI utilities.
- **`/admin` (Admin Console):** Management hub for department managers, HR, and system administrators to monitor team attendance, approve multi-level requests, define multi-region (`TW` / `JP`) work calendars, configure dynamic forms, and manage IAM permissions.

---

## Implemented Capabilities (Phase 0 – Phase 4)

Per [ADR-0007](./docs/adr/ADR-0007-project-naming.md), this section documents what is currently implemented and verified in the repository:

- **Phase 0 — Foundation (`v0.1.0-foundation`):**
  - Spring Boot 3.3.5 (Java 21 LTS) modular monolith skeleton with unified `ApiResponse<T>`, `PageResult<T>`, `BusinessException`, and `GlobalExceptionHandler`.
  - Three-tier trilingual i18n (`zh-TW`, `en-US`, `ja-JP`) via Spring `MessageSource` (`messages_*.properties`) and strict UTC timestamp storage ([ADR-0002](./docs/adr/ADR-0002-three-tier-i18n-architecture.md)).
  - Security & network utilities: `ClientIpResolver` (proxy-aware client IP extraction), `HmacSignatureVerifier` (`HMAC-SHA256` ±5 min anti-replay verification), and `LocalFileStorageService` (UUID date-partitioned storage with `SHA-256` checksums).
- **Phase 1 — Identity, Department Tree & Access Control (`v0.2.0-iam`):**
  - Flyway `V1__init_iam_schema.sql` (PostgreSQL 16 & H2 dialects): `iam_department` (with `timezone` and materialized `tree_path`), `iam_user`, `iam_role`, `iam_permission`, `iam_user_role`, `iam_role_permission`, and `iam_audit_log`.
  - Stateless JWT authentication (in-memory Access Token + `HttpOnly` Refresh Token cookie), `BCrypt` (strength 12) password hashing, 5-failure 15-minute account lockout (`locked_until`), and **DBA emergency plaintext/`{noop}` recovery** with mandatory password-change gate (`ErrorCode 1003`, [ADR-0004](./docs/adr/ADR-0004-rbac-and-department-data-scope.md)).
  - Transparent field-level encryption via `AesGcmCipher` & `AesGcmStringConverter` (`AES-256-GCM` with 96-bit random IV and `{v1}` version prefix).
  - `@DataScope` AOP aspect (`ALL`, `DEPT_AND_SUB`, `DEPT_ONLY`, `SELF_ONLY`) filtering queries by department `tree_path`, plus `@AuditLog` AOP aspect recording administrative mutations.
- **Phase 2 — Frontend Three-Hall Shell, IAM Console & `hire_date` (`v0.3.0-frontend-shell`):**
  - Vue 3.5 + Vite 6 + TypeScript + PrimeVue 4 (`AtriaBluePreset` Aura theme) + Pinia + Vue Router 4 + `vue-i18n` SPA designed with a high-density Japanese B2B layout.
  - Axios interceptor with automatic JWT attachment, silent token refresh (`/api/v1/auth/refresh`), `1003` mandatory password-change redirection (`/change-password`), and `Accept-Language` propagation.
  - Three layout shells (`PublicLayout` `/public`, `PortalLayout` `/portal`, `AdminLayout` `/admin`) plus `/login` and `/change-password`.
  - Full `/admin/iam/*` management suite:
    - **Departments (`DepartmentManageView.vue`):** Hierarchical `TreeTable` with root/child creation context, cascading `tree_path`, leader assignment, regional timezone, and `calendar_code` (`TW` / `JP`).
    - **Users (`UserManageView.vue`):** Paginated `DataTable` with search, localized role badges, `hireDate` (`DatePicker`), admin password reset, manual lockout unlock, and soft deletion.
    - **Roles & Permissions (`RoleManageView.vue`):** Role CRUD with `DataScopeType` selector and interactive permission matrix.
    - **Audit Logs (`AuditLogView.vue`):** Paginated administrative audit trail viewer.
  - Flyway `V2__add_user_hire_date.sql` (`iam_user.hire_date DATE`) wired end-to-end across JPA entity, DTOs, integration tests, and admin UI ([ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) Decision 11).
- **Phase 3a — Attendance Core, Multi-Region Calendar & Employee Portal (`v0.4.0-attendance-core`):**
  - Flyway `V3__init_attendance_core_schema.sql` (PostgreSQL 16 & H2 dialects): `iam_department.calendar_code`, `att_work_calendar` (`WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY`, `STATUTORY_REST_DAY`, `NATIONAL_HOLIDAY`), `att_punch_rule`, append-only `att_punch_log`, derived `att_daily_record` (with `@Version` optimistic locking), and `att_monthly_close`.
  - **Multi-Region Work Calendar & Statutory Year Generator (`WorkCalendarService`, `StatutoryHolidayGenerator`):** Built-in Taiwan (`TW` lunar & solar statutory holidays + observed weekend compensation) and Japan (`JP` Cabinet Office holidays, Happy Monday, astronomical vernal/autumnal equinoxes, substitute holidays *Furikae Kyūjitsu*, and Citizens' Holidays *Kokumin no Kyūjitsu*) full-year generators, with individual day overrides and regional punch rules for HR.
  - **Immutable Punch Ledger & Daily Derivation (`PunchService`):** Web self-service clock-in/out (`/api/v1/attendance/punch`) and `HMAC-SHA256` anti-replay Bot proxy punch (`/api/v1/attendance/bot/punch`), dual-layer append-only protection on `att_punch_log` (PostgreSQL `BEFORE UPDATE OR DELETE` trigger `trg_att_punch_log_immutable` + JPA `@Immutable` / `@PreUpdate` / `@PreRemove`), 60-second duplicate punch guard (`ErrorCode 2001`), logical day attribution with `04:00 AM` cross-midnight cutoff, grace/rounding calculation, and automatic 1-hour lunch break (`12:00`–`13:00`) deduction.
  - **Daily Settlement & Monthly Closing (`DailySettlementJob`, `MonthlyCloseService`):** Scheduled & manual post-cutoff settlement marking `MISSING_PUNCH` / `ABSENT` on past workdays, plus monthly payroll lock (`att_monthly_close`) with `PendingRequestChecker` SPI (`ErrorCode 2010`), closed-period mutation guard (`ErrorCode 2005`), and HR reopen support.
  - **Architectural Fitness & CI Verification (`ModularArchitectureTest`, `.github/workflows/ci.yml`):** 5 ArchUnit rules enforcing ADR-0001 modular monolith boundaries, PostgreSQL 16 + H2 dual-engine CI pipeline (`pgtest` profile), and bug-catching ESLint 9 flat config.
  - **Frontend ESS Portal & HR Attendance Console:**
    - **Employee Portal (`PortalHomeView.vue`):** Live regional clock card, one-click punch with append-only raw log feed, monthly KPI strip, and interactive 7-column monthly attendance & holiday calendar with day-detail modal.
    - **HR Calendar & Punch Rules (`CalendarManageView.vue`):** Visual `TW`/`JP` monthly calendar editor, full-year template generator modal, day override dialog, and regional punch rule configuration.
    - **HR Monthly Close & Settlement (`MonthlyCloseView.vue`):** Pre-close blocking check, month lock/reopen controls, manual daily settlement trigger, and closing audit history.
- **Phase 3b — Leave Ledger, Statutory Overtime & Approval Event Contract (`v0.4.5-leave-overtime`):**
  - Flyway `V4__init_leave_overtime_schema.sql` (PostgreSQL 16 & H2 dialects): `att_leave_type` (`min_unit_minutes`), `att_leave_grant_rule` (`ANNIVERSARY` / `CALENDAR_YEAR`), `att_request` (`PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `REVOKED`), `att_comp_leave_grant` (`band_breakdown_json`), `att_leave_balance` (`source_comp_grant_id`), `att_leave_balance_tx` (`FREEZE`, `COMMIT`, `RESTORE_PENDING`, `RESTORE_USED`), `att_overtime_rate_rule` (`TW` `1.34`/`1.67`/`2.67`/`2.00` & `JP` `1.25`/`1.35`), `att_overtime_cap_rule` (`WARN`/`BLOCK`), and FK `fk_att_punch_log_correction_request`.
  - **Seniority-Based Annual Leave Grant Engine (`AnnualLeaveGrantService`, `LeaveTypeService`):** Evaluates `iam_user.hire_date` on **Anniversary Basis (`ANNIVERSARY`)** against Taiwan LSA Art. 38 (including 10+ yr `+1 day/yr` up to 30 days) and Japan LSA Art. 39 (0.5y, 1.5y… up to 20 days, tracking the `2400`-minute mandatory 5-day utilization obligation when granted ≥ 10 days), reporting accounts missing `hire_date`.
  - **Leave Duration Calculator & FIFO Balance Ledger (`LeaveDurationCalculatorImpl`, `LeaveBalanceLedgerService`):** Intersects requested UTC intervals with department work calendar, deducts `12:00`–`13:00` lunch break, rounds up to `min_unit_minutes`, freezes/deducts across `att_leave_balance` buckets by **`valid_until ASC` (FIFO)**, and logs every per-bucket delta in `att_leave_balance_tx` for 100% accurate multi-bucket restoration on rejection, withdrawal, or revocation.
  - **Regional Overtime Rate/Cap Engine & Comp-Leave Conversion (`OvertimeRuleAndCalculatorService`, `CompLeaveGrantService`):** Evaluates cumulative-minute rate bands with `BigDecimal` + `RoundingMode.CEILING` to whole minutes, checks monthly/multi-month/yearly statutory caps (`WARN` / `BLOCK`), converts `COMP_LEAVE` (`HOURS_1_TO_1` or `RATE_MULTIPLIED`) into `att_comp_leave_grant` + linked `att_leave_balance` bucket, and settles expired comp-leave back to original overtime rate bands via `band_breakdown_json` (TW LSA Art. 32-1).
  - **Unidirectional Approval Event Contract & Daily Recomputation (`AttendanceRequestService`, `AttendanceRecordCalculator`, `DatabasePendingRequestChecker`, `AttRequestGuardImpl`, `AttendanceRequestStubController`):** Cycle-free events in `attendance.event`, shift lateness/early-leave threshold adjustment when approved morning/afternoon leave covers shift boundaries, immutable `att_punch_log(CORRECTION)` append on punch correction, real DB pending request check blocking monthly close (`2010`), comp-leave usage revocation guard (`2025`), and `@Profile({"local", "pgtest"})` dev/test approval stub.
  - **Frontend ESS Leave/Overtime Workspace & HR Rule Console (`PortalHomeView.vue`, `LeaveOvertimeManageView.vue`):** Leave balance FIFO cards, 90-day expiring comp-leave banner, JP 5-day obligation progress bar, Leave/Overtime/Punch Correction application dialogs, My Requests table with withdraw & dev stub actions, and 5-tab HR management console (`/admin/attendance/leave-overtime`).
- **Phase 4 — Dynamic Form & Multi-Level Approval Workflow + In-App Notifications & Leave Digest (`v0.5.0-workflow`):**
  - Flyway `V5__init_workflow_and_notification_schema.sql` (PostgreSQL 16 & H2 dialects): 9 new tables (`wf_form_template`, `wf_flow_definition`, `wf_flow_node`, `wf_flow_node_condition`, `wf_instance`, `wf_task_log`, `wf_deputy`, `ntf_notification`, `ntf_digest_log`) plus permissions `wf:instance:self`, `wf:template:read`, `wf:template:write`, `wf:instance:reassign`, `ntf:digest:trigger`.
  - **Dynamic Form Schema & Immutable Versioning (`wf_form_template`, `wf_instance`):** JSON Schema field definitions (`TEXT`, `TEXTAREA`, `NUMBER`, `SELECT`, `DATE`), automatic version bumping on template schema updates (`v1` -> `v2`), and immutable `form_version` + `schema_snapshot_json` preserved on every submitted `wf_instance`.
  - **SpEL-Free Structured Condition Multi-Level Approval Engine (`StructuredConditionEvaluator`, `ApproverResolverService`, `WorkflowEngineService`):** Pure Java evaluation of `wf_flow_node_condition` (`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, same `logical_group` = AND, across groups = OR), department tree walk-up (`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`) with `HR_ADMIN` -> `SUPER_ADMIN` fallback, and `selfApprovalAutoSkip`.
  - **Bidirectional Attendance Event Loop (`attendance.event.*`):** `AttRequestSubmittedEvent` / `AttRequestCancelledEvent` auto-create/cancel linked `wf_instance`; workflow approval/rejection/revocation publishes `AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent` (with pre-revoke validation via `AttRequestGuard`).
  - **Return-to-Draft (`差戻し`) & Field-by-Field Revision Diff (`diff_json`):** Supported on standalone dynamic forms (`target_type = 'DYNAMIC_FORM'`), blocked on attendance-linked requests (`3009` `WF_RETURN_TO_DRAFT_NOT_ALLOWED_FOR_ATT_REQUEST` to protect FIFO frozen leave buckets).
  - **Deputy Delegation (`wf_deputy`) & HR Force Reassign (`REASSIGN`):** Date-bounded deputy rules with self-delegation (`3012`) and overlap (`3013`) guards, dual audit logging (`actor_user_id` + `delegator_user_id` in `wf_task_log`), and admin forced task reassignment.
  - **In-App Notifications & Idempotent Monthly Leave Digest (`notification` module):** `NotificationBell.vue` in Portal & Admin topbars, dev email sink (`EmailNotificationChannel`), and `LeaveBalanceDigestJob` (`ntf_digest_log` with `UNIQUE (user_id, period_year_month)` idempotency).
  - **Frontend Views & Verification Tools:** `NotificationBell.vue` (topbar bell & drawer), `PortalWorkflowPanel.vue` (mounted in `/portal`), `WorkflowManageView.vue` (`/admin/workflow/manage`), and zero-browser static UI/i18n gate `frontend/scripts/check-ui-i18n.mjs` verifying 485 trilingual keys across `zh-TW.ts`, `en-US.ts`, and `ja-JP.ts`.

---

## Tech Stack

| Layer | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend** | Java 21 LTS, Spring Boot 3.3.5 | Modular monolith core (`io.github.skr416.atria`) |
| **Security & Crypto** | Spring Security 6, JWT (`jjwt`), BCrypt, `AES-256-GCM` | Stateless auth, DBA emergency recovery (`1003`), RBAC, `@DataScope` subtree filtering, and field-level encryption (`AesGcmStringConverter`) |
| **Database** | PostgreSQL 16 (H2 for local zero-install demo), Flyway (`V1`–`V5`) | Relational core with native `JSONB` for trilingual fields & dynamic forms, plus partial unique indexes for soft delete |
| **Frontend** | Vue 3.5, Vite 6, TypeScript, PrimeVue 4, Pinia, Vue Router 4, Lucide Icons, ApexCharts | High-density B2B SPA with three shells (`/public`, `/portal`, `/admin`) and SCRM-compliant MIT dependencies ([ADR-0001](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)) |
| **i18n** | Spring `MessageSource`, `vue-i18n` | Trilingual support (`zh-TW`, `en-US`, `ja-JP`) across API, DB, and UI |
| **API Contract** | Springdoc OpenAPI 3 | Swagger documentation and frontend TypeScript type alignment |

---

## Repository Structure

```text
atria/
├── .github/workflows/ci.yml      # GitHub Actions CI (H2 + PostgreSQL 16 Flyway/Trigger + ESLint & Vite)
├── docs/
│   ├── ROADMAP.md                # Milestone plan and Git tag schedule (Phase 0 to Phase 5)
│   ├── ARCHITECTURE.md           # Modular monolith layout and database schema overview
│   ├── NEXT_STEPS.md             # Cross-session handover & active checklist
│   └── adr/                      # Architecture Decision Records (ADR-0001 to ADR-0008)
│       └── zh-TW/                # Traditional Chinese ADR translations & index
├── backend/                      # Maven artifactId: atria-backend (Spring Boot 3.3.5, Java 21)
│   └── src/
│       ├── main/java/io/github/skr416/atria/
│       │   ├── common/           # ApiResponse, ErrorCode, AesGcmCipher, HmacSignatureVerifier, etc.
│       │   ├── config/           # I18nConfig, OpenApiConfig, CorsConfig
│       │   ├── iam/              # Auth, Users, Dept Tree, Roles, Permissions, @DataScope, @AuditLog
│       │   ├── attendance/       # TW/JP Calendar, Punch Log & Daily Record, Leave FIFO Ledger, Overtime/Comp-Leave, Events & Stub
│       │   ├── workflow/         # Dynamic Forms, Multi-Level Approval Engine, Node Conditions, Instances, Deputies
│       │   └── notification/     # In-App Notifications, Email Sink, Monthly Leave Balance Digest Job
│       ├── main/resources/
│       │   ├── db/migration/     # Flyway V1–V5 SQL migrations (postgresql/ & h2/)
│       │   └── i18n/             # Trilingual message bundles (zh_TW, en_US, ja_JP)
│       └── test/                 # IAM, Attendance, Workflow/Notification, Immutability & ArchUnit tests (40 tests)
└── frontend/                     # npm package: atria-frontend (Vue 3.5, Vite 6, TypeScript, PrimeVue 4)
    ├── scripts/                  # check-ui-i18n.mjs (485-key trilingual parity, Button label anti-stacking & data-testid gate)
    └── src/
        ├── api/                  # Axios HTTP client (JWT refresh & 1003 guard) + IAM, Attendance & Workflow APIs
        ├── components/
        │   ├── common/           # Trilingual LanguageSwitcher & NotificationBell
        │   └── portal/           # PortalWorkflowPanel (My Requests & Pending Approval Inbox)
        ├── i18n/                 # vue-i18n locales (zh-TW, en-US, ja-JP)
        ├── layouts/              # PublicLayout, PortalLayout, AdminLayout
        ├── router/               # Three-Hall route guards & role/password-change redirection
        ├── stores/               # Pinia auth store (in-memory access token & user profile)
        └── views/                # Public, Portal, Auth, and Admin (IAM, Attendance, Leave/OT & Workflow Manage) views
```

---

## Quick Start (Local Development)

### 1. Backend (`atria-backend`)
Runs on port `8080` with an in-memory H2 database (`local` profile) and seeded demo accounts (`admin`, `hr_lead`, `eng_mgr`, `emp_alice`, `emp_locked`; password: `Password123!`):
```bash
cd backend
./mvnw spring-boot:run
```

### 2. Frontend (`atria-frontend`)
Runs on port `5173` and proxies `/api` to `http://localhost:8080`:
```bash
cd frontend
npm install
npm run dev
```

---

## Implementation Roadmap & Status

| Phase | Target Tag | Scope | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton, `ApiResponse<T>`, trilingual `ErrorCode` i18n | ✅ Completed |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), JWT auth, RBAC, department tree, `@DataScope`, `@AuditLog`, `AES-256-GCM` | ✅ Completed |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 shell (`atria-frontend`), three layouts (`/public`, `/portal`, `/admin`), IAM admin UI, `hire_date` (`V2`) | ✅ Completed |
| **Phase 3a** | `v0.4.0-attendance-core` | Multi-region calendar (`TW`/`JP` templates), immutable `att_punch_log` + derived `att_daily_record`, bot punch, daily settlement, monthly close, `/portal` & `/admin` calendar UI | ✅ Completed |
| **Phase 3b** | `v0.4.5-leave-overtime` | Leave types & seniority grant rules (`hire_date`), leave calculator & balance ledger, overtime rate/cap engine, comp-leave conversion, approval event contract + dev/test stub | ✅ Completed |
| **Phase 4** | `v0.5.0-workflow` | Dynamic form templates (`wf_form_template`), structured-condition approval workflow (`wf_*`), approver fallback, deputy delegation (`wf_deputy`), notifications (`ntf_*`) & monthly digest | ✅ Completed |
| **Phase 5** | `v1.0.0` | Public Hall corporate announcements (`ann_*`), manager dashboard & payroll export, async AI voice-to-text (`ai_stt_job`), and final release polish | ⏳ Next |

---

## Architecture Decision Records (ADRs)

All architectural decisions and trade-offs are documented in English under [`docs/adr/`](./docs/adr/) with full Traditional Chinese translations indexed in [`docs/adr/zh-TW/README.md`](./docs/adr/zh-TW/README.md):

| ADR | Title | Status |
| :--- | :--- | :--- |
| [ADR-0001](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md) ([繁中](./docs/adr/zh-TW/ADR-0001-modular-monolith-and-tech-stack.md)) | Modular Monolith Architecture and Core Tech Stack | Accepted |
| [ADR-0002](./docs/adr/ADR-0002-three-tier-i18n-architecture.md) ([繁中](./docs/adr/zh-TW/ADR-0002-three-tier-i18n-architecture.md)) | Three-Tier Trilingual i18n Architecture (`zh-TW`, `en-US`, `ja-JP`) | Accepted |
| [ADR-0003](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md) ([繁中](./docs/adr/zh-TW/ADR-0003-three-halls-and-calendar-driven-attendance.md)) | Three-Hall Routing and Calendar-Driven Attendance Model | Accepted (refined by [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md)) |
| [ADR-0004](./docs/adr/ADR-0004-rbac-and-department-data-scope.md) ([繁中](./docs/adr/zh-TW/ADR-0004-rbac-and-department-data-scope.md)) | RBAC Combined with Department Tree Data Scope & 3-Tier Security/Encryption | Accepted |
| [ADR-0005](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md) ([繁中](./docs/adr/zh-TW/ADR-0005-dynamic-form-versioning-and-approval-workflow.md)) | Dynamic Form Versioning and Multi-Level Approval Workflow | Accepted (refined by [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md)) |
| [ADR-0006](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md) ([繁中](./docs/adr/zh-TW/ADR-0006-ai-module-scope-and-async-stt.md)) | Practical AI Module Scope (Chat, Text Transformation, and Async STT) | Accepted |
| [ADR-0007](./docs/adr/ADR-0007-project-naming.md) ([繁中](./docs/adr/zh-TW/ADR-0007-project-naming.md)) | Project Naming ("Atria") and Schema Prefix Alignment (`iam_`, `att_`, `wf_`, `ntf_`, `ann_`, `ai_`) | Accepted |
| [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) ([繁中](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md)) | Attendance, Leave & Overtime Refinements and Phase 3/4 Sequencing (TW/JP Labor Standards) | Accepted |

---

## License

Licensed under the [MIT License](./LICENSE).
