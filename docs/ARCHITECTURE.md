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
| **`iam`** | `iam_` | `iam_user`<br>`iam_department`<br>`iam_role`<br>`iam_permission`<br>`iam_audit_log` | • `iam_department` stores `parent_id` and `leader_id` for hierarchical approval routing and manager data-scope filtering.<br>• Multilingual names use `JSONB` (`{"zh-TW": "...", "en-US": "...", "ja-JP": "..."}`). |
| **`attendance`** | `att_` | `att_work_calendar`<br>`att_punch_record`<br>`att_leave_type`<br>`att_request` | • `att_work_calendar` defines `WORKDAY`, `HOLIDAY`, and `MAKEUP_DAY` with standard shift hours.<br>• `att_request` unifies both leave applications and punch corrections via `request_type` (`LEAVE` \| `PUNCH_CORRECTION`). |
| **`workflow`** | `wf_` | `wf_form_template`<br>`wf_flow_definition`<br>`wf_instance`<br>`wf_task_log`<br>`wf_deputy` | • `wf_form_template` stores field definitions in `schema_json JSONB` with an immutable `version` number.<br>• `wf_deputy` handles time-bounded approval delegation when a manager is on leave. |
| **`announcement`** | `ann_` | `ann_notice`<br>`ann_read_receipt` | • `scope` distinguishes `PUBLIC` (`/public`) from `INTERNAL` (`/portal`). |
| **`ai`** | `ai_` | `ai_chat_session`<br>`ai_chat_message`<br>`ai_stt_job`<br>`ai_usage_quota` | • `ai_stt_job` tracks asynchronous audio-to-text transcription jobs (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`). |
