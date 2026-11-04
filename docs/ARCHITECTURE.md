# Atria System Architecture

**Atria** (*Attendance & Approval Workflow Platform*) is designed as a **Modular Monolith** with a **Three-Hall SPA Frontend** around a single Spring Boot 3 backend and PostgreSQL database.

---

## 1. High-Level Topology ("Three Halls Around One Hub")

```text
                        ┌─────────────────────────────────────────┐
                        │     atria-backend (Spring Boot 3)       │
                        │     Package: io.github.skr416.atria     │
                        │   PostgreSQL 16 + Flyway + JWT + i18n   │
                        └────────────────────┬────────────────────┘
                                             │
            ┌────────────────────────────────┼────────────────────────────────┐
            ▼                                ▼                                ▼
┌───────────────────────┐      ┌───────────────────────────┐      ┌───────────────────────┐
│   1. Public Site      │      │  2. Employee Portal (ESS) │      │   3. Admin Console    │
│     (/public/*)       │      │        (/portal/*)        │      │      (/admin/*)       │
├───────────────────────┤      ├───────────────────────────┤      ├───────────────────────┤
│ • Unauthenticated     │      │ • All authenticated staff │      │ • Managers, HR, Admin │
│ • External notices    │      │ • Clock-in / Clock-out    │      │ • Attendance KPI      │
│ • Category filter     │      │ • Interactive work        │      │   dashboard & logs    │
│ • Pinned notices      │      │   calendar                │      │ • Multi-level         │
│ • Trilingual switch   │      │ • Leave & punch-          │      │   approval workbench  │
│   (zh-TW / EN / JA)   │      │   correction requests     │      │ • HR calendar builder │
│                       │      │ • Internal notices        │      │ • Dynamic form editor │
│                       │      │ • AI Chat, Text Transform │      │ • IAM (Users, Depts,  │
│                       │      │   & Audio STT             │      │   Roles, Permissions) │
└───────────────────────┘      └───────────────────────────┘      └───────────────────────┘
```

---

## 2. Backend Package Structure (`io.github.skr416.atria`)

```text
backend/src/main/java/io/github/skr416/atria/
├── AtriaApplication.java
├── common/
│   ├── api/                    # ApiResponse<T>, PageResult<T>, ErrorCode
│   ├── exception/              # BusinessException, GlobalExceptionHandler
│   ├── audit/                  # BaseAuditEntity, AuditLogAspect (@AuditLog)
│   ├── notification/           # NotificationChannel (InApp, Email, WebhookBot)
│   ├── export/                 # ExcelExportService (Apache POI SXSSF), CsvExportService
│   └── storage/                # FileStorageService (LocalFileStorageService, etc.)
├── config/                     # SecurityConfig, I18nConfig, OpenApiConfig, CorsConfig
├── security/                   # JwtAuthenticationFilter, CurrentUser, DataScopeAspect
└── modules/
    ├── iam/                    # Users, Department Tree, Roles, Permissions, Audit Logs
    ├── attendance/             # Corporate Work Calendar, Daily Punch, Leave & Punch Requests
    ├── workflow/               # Dynamic Form Templates, Approval Flows, Instances, Deputies
    ├── dashboard/              # Manager Attendance Overview & Anomaly Aggregation
    ├── announcement/           # Public & Internal Announcements + Read Receipts
    └── ai/                     # Chat Sessions, Workplace Text Transform, Async Audio STT
```

---

## 3. Database Schema & Prefix Alignment

Every domain module maps 1-to-1 to a dedicated table prefix:

| Module | Prefix | Tables | Key Design Notes |
| :--- | :---: | :--- | :--- |
| **`iam`** | `iam_` | `iam_user`<br>`iam_department`<br>`iam_role`<br>`iam_permission`<br>`iam_audit_log` | • `iam_department` stores `parent_id`, `leader_id`, `timezone` (e.g. `Asia/Taipei`, `Asia/Tokyo`), and `calendar_code` (`TW` / `JP`) for cross-region cutoffs and statutory holiday templates.<br>• `iam_user` stores `hire_date DATE` (`V2`) for seniority-based annual leave grants, enforces lockout after 5 failed attempts, and uses a PostgreSQL partial unique index (`WHERE is_deleted = FALSE`) for soft delete.<br>• Multilingual names use `JSONB` (`{"zh-TW": "...", "en-US": "...", "ja-JP": "..."}`). |
| **`attendance`** | `att_` | `att_work_calendar`<br>`att_punch_log`<br>`att_daily_record`<br>`att_punch_rule`<br>`att_leave_type`<br>`att_leave_grant_rule`<br>`att_leave_balance`<br>`att_overtime_rate_rule`<br>`att_overtime_cap_rule`<br>`att_comp_leave_grant`<br>`att_monthly_close`<br>`att_request` | • `att_work_calendar` is scoped by `calendar_code` (`TW`, `JP`) with legal day types (`WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY`, `STATUTORY_REST_DAY`, `NATIONAL_HOLIDAY`) and built-in statutory year generators (`ADR-0008`).<br>• Punch data is split into append-only `att_punch_log` (`raw_punch_time` UTC, `client_ip`, `source` `WEB` \| `BOT` \| `CORRECTION`) and derived `att_daily_record` (`work_date`, `effective_clock_in/out`, `worked_minutes`, `status`, `@Version`), honoring `day_cutoff_hour` (`04:00` AM) and 1-hour lunch break deduction.<br>• `att_leave_type` (`min_unit_minutes`), `att_leave_grant_rule` (`TW`/`JP` seniority tables), and `att_leave_balance` (`total_hours`, `used_hours`, `pending_hours`) manage statutory and manual leave.<br>• `att_overtime_rate_rule`, `att_overtime_cap_rule`, and `att_comp_leave_grant` track weighted overtime minutes, statutory caps, and comp-leave conversion (`HOURS_1_TO_1` / `RATE_MULTIPLIED`).<br>• `att_monthly_close` blocks closing while any `PENDING` request touches that month.<br>• `att_request` unifies `LEAVE`, `PUNCH_CORRECTION`, and `OVERTIME`, decoupled from Phase 4 via `AttRequestApprovedEvent` / `Rejected` / `Revoked`. |
| **`workflow`** | `wf_`<br>`ntf_` | `wf_flow_definition`<br>`wf_flow_node`<br>`wf_flow_node_condition`<br>`wf_form_template`<br>`wf_instance`<br>`wf_task_log`<br>`wf_deputy`<br>`ntf_*` | • Table-driven rules: `wf_flow_node_condition` stores structured rows (`field_key`, `operator`, `value`, logical group) evaluated in Java without SpEL.<br>• Approver resolution walks up the department tree with `HR` role fallback; supports Return-to-Draft with Diff, applicant revocation, and HR forced task reassignment (`REASSIGN`).<br>• Enforces JPA `@Version` optimistic locking and time-bounded `wf_deputy` delegation.<br>• `ntf_*` notification tables power in-app & email notifications (`LeaveBalanceDigestJob`). |
| **`announcement`** | `ann_` | `ann_notice`<br>`ann_read_receipt` | • `scope` distinguishes `PUBLIC` (`/public`) from `INTERNAL` (`/portal`). |
| **`ai`** | `ai_` | `ai_chat_session`<br>`ai_chat_message`<br>`ai_stt_job`<br>`ai_usage_quota` | • `ai_stt_job` tracks asynchronous audio-to-text transcription jobs (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`), powered by standalone `whisper.cpp` HTTP service. |
