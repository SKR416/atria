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
│   ├── audit/                  # BaseAuditEntity
│   ├── controller/             # HealthCheckController
│   ├── crypto/                 # AesGcmCipher, AesGcmStringConverter (AES-256-GCM {v1})
│   ├── exception/              # BusinessException, GlobalExceptionHandler
│   ├── notification/           # NotificationChannel, NotificationMessage
│   ├── storage/                # FileStorageService, LocalFileStorageService
│   └── util/                   # ClientIpResolver, HmacSignatureVerifier
├── config/                     # I18nConfig, OpenApiConfig, CorsConfig
├── iam/                        # Phase 1–2 (Completed):
│   ├── audit/                  # @AuditLog, AuditLogAspect
│   ├── controller/             # Auth, Department, User, Role, AuditLog controllers
│   ├── datascope/              # @DataScope, DataScopeAspect, DataScopeContext
│   ├── dto/                    # Auth, Dept, User (with hireDate), Role DTOs
│   ├── model/                  # User, Department, Role, Permission, AuditLogEntry, DataScopeType
│   ├── repository/             # JPA repositories with partial unique index support
│   ├── security/               # SecurityConfig, JwtAuthenticationFilter, JwtTokenService, AtriaUserPrincipal
│   └── service/                # AuthService, DepartmentService, UserService, RoleService
├── attendance/                 # Phase 3a–3b (Completed):
│   ├── controller/             # WorkCalendar, Punch, MonthlyClose, LeaveAndOvertime, AttendanceRequestStub controllers
│   ├── dto/                    # Calendar, Punch, MonthlyOverview, MonthlyClose, Leave, Overtime, Request DTOs
│   ├── event/                  # AttRequestSubmittedEvent, Cancelled, Approved, Rejected, Revoked (unidirectional contract)
│   ├── model/                  # WorkCalendarDay, PunchRule, PunchLog, DailyAttendanceRecord, MonthlyCloseRecord, LeaveType, LeaveGrantRule, LeaveBalance, LeaveBalanceTx, OvertimeRateRule, OvertimeCapRule, CompLeaveGrant, AttendanceRequest + Enums
│   ├── repository/             # JPA repositories for att_* tables
│   └── service/                # WorkCalendarService, StatutoryHolidayGenerator, PunchService, AttendanceRecordCalculator, DailySettlementJob, MonthlyCloseService, LeaveTypeService, AnnualLeaveGrantService, LeaveDurationCalculator, LeaveBalanceLedgerService, OvertimeRuleAndCalculatorService, CompLeaveGrantService, AttendanceRequestService, AttRequestGuard, DatabasePendingRequestChecker
├── workflow/                   # Phase 4 (Completed):
│   ├── controller/             # WorkflowController (/api/v1/workflow/*)
│   ├── dto/                    # FormTemplate, FlowDefinition, FlowNode, Instance, TaskAction, Reassign, Deputy DTOs
│   ├── model/                  # FormTemplate, FlowDefinition, FlowNode, FlowNodeCondition, WorkflowInstance, WorkflowTaskLog, WorkflowDeputy + Enums
│   ├── repository/             # FormTemplate, FlowDefinition, FlowNode, FlowNodeCondition, WorkflowInstance, WorkflowTaskLog, WorkflowDeputy repositories
│   └── service/                # FormTemplateAndFlowService, WorkflowEngineService, StructuredConditionEvaluator, ApproverResolverService
├── notification/               # Phase 4 (Completed):
│   ├── controller/             # NotificationController (/api/v1/notifications/*)
│   ├── dto/                    # NotificationResponse, NotificationSendCommand, DigestTriggerRequest, DigestBatchResultDto
│   ├── model/                  # NotificationRecord, DigestDeliveryLog, NotificationCategory
│   ├── repository/             # NotificationRecordRepository, DigestDeliveryLogRepository
│   └── service/                # NotificationService, NotificationServiceImpl, EmailNotificationChannel, LeaveBalanceDigestJob
├── dashboard/                  # Phase 5: Manager Attendance Overview, Anomaly Filtering & Payroll Export
├── announcement/               # Phase 5: Public & Internal Announcements + Read Receipts
└── ai/                         # Phase 5: Chat Sessions, Workplace Text Transform, Async Audio STT
```

---

## 3. Database Schema & Prefix Alignment

Every domain module maps 1-to-1 to a dedicated table prefix:

| Module | Prefix | Tables | Key Design Notes |
| :--- | :---: | :--- | :--- |
| **`iam`** | `iam_` | `iam_user`<br>`iam_department`<br>`iam_role`<br>`iam_permission`<br>`iam_audit_log` | • `iam_department` stores `parent_id`, `leader_id`, `timezone` (e.g. `Asia/Taipei`, `Asia/Tokyo`), and `calendar_code` (`TW` / `JP`) for cross-region cutoffs and statutory holiday templates.<br>• `iam_user` stores `hire_date DATE` (`V2`) for seniority-based annual leave grants, enforces lockout after 5 failed attempts, and uses a PostgreSQL partial unique index (`WHERE is_deleted = FALSE`) for soft delete.<br>• Multilingual names use `JSONB` (`{"zh-TW": "...", "en-US": "...", "ja-JP": "..."}`). |
| **`attendance`** | `att_` | `att_work_calendar`<br>`att_punch_log`<br>`att_daily_record`<br>`att_punch_rule`<br>`att_leave_type`<br>`att_leave_grant_rule`<br>`att_leave_balance`<br>`att_leave_balance_tx`<br>`att_overtime_rate_rule`<br>`att_overtime_cap_rule`<br>`att_comp_leave_grant`<br>`att_monthly_close`<br>`att_request` | • `att_work_calendar` is scoped by `calendar_code` (`TW`, `JP`) with legal day types (`WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY`, `STATUTORY_REST_DAY`, `NATIONAL_HOLIDAY`) and built-in statutory year generators (`ADR-0008`).<br>• Punch data is split into append-only `att_punch_log` (`raw_punch_time` UTC, `client_ip`, `source` `WEB` \| `BOT` \| `CORRECTION`, protected by PostgreSQL trigger `trg_att_punch_log_immutable` and JPA `@Immutable`) and derived `att_daily_record` (`work_date`, `effective_clock_in/out`, `worked_minutes`, `leave_minutes`, `overtime_minutes`, `status`, `@Version`), honoring `day_cutoff_hour` (`04:00` AM) and 1-hour lunch break deduction.<br>• `att_leave_type` (`min_unit_minutes`), `att_leave_grant_rule` (`TW`/`JP` seniority tables on `ANNIVERSARY` basis), `att_leave_balance` (`granted_minutes`, `used_minutes`, `pending_minutes`, `source_comp_grant_id`), and `att_leave_balance_tx` (`FREEZE`, `COMMIT`, `RESTORE_PENDING`, `RESTORE_USED`) manage statutory, manual, and comp-leave buckets with FIFO (`valid_until ASC`) deduction and 100% exact per-bucket restoration.<br>• `att_overtime_rate_rule` (TW `1.34`/`1.67`/`2.67`/`2.00`, JP `1.25`/`1.35`), `att_overtime_cap_rule` (`WARN`/`BLOCK`), and `att_comp_leave_grant` (`band_breakdown_json`) track weighted overtime minutes (`BigDecimal` + `RoundingMode.CEILING`), statutory caps, comp-leave conversion (`HOURS_1_TO_1` / `RATE_MULTIPLIED`), and TW LSA Art. 32-1 expiry payout restoration.<br>• `att_monthly_close` blocks closing while any `PENDING` request touches that month.<br>• `att_request` unifies `LEAVE`, `PUNCH_CORRECTION`, and `OVERTIME` (`PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `REVOKED`), decoupled from Phase 4 via unidirectional events in `attendance.event` (`AttRequestSubmittedEvent`, `Cancelled`, `Approved`, `Rejected`, `Revoked`). |
| **`workflow`** | `wf_` | `wf_form_template`<br>`wf_flow_definition`<br>`wf_flow_node`<br>`wf_flow_node_condition`<br>`wf_instance`<br>`wf_task_log`<br>`wf_deputy` | • Form schema versioning: `wf_form_template` stores JSON Schema field definitions (`TEXT`, `TEXTAREA`, `NUMBER`, `SELECT`, `DATE`) and auto-bumps version (`v1` -> `v2`), while `wf_instance` permanently preserves immutable `form_version` + `schema_snapshot_json`.<br>• Structured conditions without SpEL: `wf_flow_node_condition` rows (`field_key`, `operator`, `value`, `logical_group`) evaluated in pure Java (`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`).<br>• Approver resolution walks up department tree (`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`) with `HR_ADMIN` -> `SUPER_ADMIN` fallback and `selfApprovalAutoSkip`.<br>• Terminal events: integrates bidirectionally with `attendance.event.*` (`AttRequestApprovedEvent`, `Rejected`, `Revoked` with `AttRequestGuard`).<br>• Return-to-Draft (`差戻し`) with field-by-field `diff_json` on dynamic forms; blocked on attendance requests (`3009`) to protect FIFO frozen leave buckets.<br>• Deputy delegation (`wf_deputy`) with date bounds, self/overlap guards, dual-user audit logging in `wf_task_log`, and HR forced reassignment (`REASSIGN`). |
| **`notification`** | `ntf_` | `ntf_notification`<br>`ntf_digest_log` | • In-app notifications with read status (`is_read`, `read_at`) and dev email sink (`EmailNotificationChannel`).<br>• Idempotent monthly leave balance digest delivery log (`ntf_digest_log`) enforced by `UNIQUE (user_id, period_year_month)` constraint via `LeaveBalanceDigestJob`. |
| **`announcement`** | `ann_` | `ann_notice`<br>`ann_read_receipt` | • `scope` distinguishes `PUBLIC` (`/public`) from `INTERNAL` (`/portal`). |
| **`ai`** | `ai_` | `ai_chat_session`<br>`ai_chat_message`<br>`ai_stt_job`<br>`ai_usage_quota` | • `ai_stt_job` tracks asynchronous audio-to-text transcription jobs (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`), powered by standalone `whisper.cpp` HTTP service. |
