# ADR-0008: Attendance, Leave & Overtime Refinements and Phase 3/4 Sequencing

- **Status:** Accepted
- **Date:** 2026-11-04
- **Refines:** ADR-0003 (attendance model), ADR-0005 (workflow engine)

## Context

A design review held while Phase 2 (frontend shell) was in progress surfaced gaps and contradictions in ADR-0003/ADR-0005:

1. ADR-0003 declares `raw_punch_time` immutable, while ADR-0005 states approved punch corrections "update `att_punch_record`". A single table cannot satisfy both, nor model multiple punches per day.
2. Departments support multiple timezones (`Asia/Taipei`, `Asia/Tokyo`), yet a single company-wide calendar cannot express different national holidays.
3. Overtime pay multipliers depend on the *legal type* of day (Taiwan: regular leave day vs. rest day vs. national holiday; Japan: statutory vs. scheduled holiday), which `WORKDAY / HOLIDAY / MAKEUP_DAY` cannot distinguish.
4. Phase 3 attendance requests need approval before the Phase 4 workflow engine exists; Phase 4 revocation notices need notifications before Phase 5.
5. H2 compatibility mode does not support PostgreSQL `JSONB` operators, which Phase 4 relies on.

**Positioning:** Atria is a portfolio project built as a *capability showcase*. Rules are designed to production-grade standards aligned with the Taiwan Labor Standards Act (勞動基準法) and the Japan Labor Standards Act (労働基準法), implemented as **rule tables with built-in TW/JP templates** rather than hardcoded logic. Seeded statutory values must be verified by HR/legal before any production use.

## Decision

### 1. Split Punch Data into an Immutable Log and a Derived Daily Record

- **`att_punch_log`** (append-only): one row per punch event — `user_id`, `raw_punch_time` (UTC, second precision), `client_ip`, `source` (`WEB` | `BOT` | `CORRECTION`), `bot_client_id`. Rows are never updated or deleted, enforced at both the ORM layer (`@Immutable`, `@PreUpdate`, `@PreRemove`) and the PostgreSQL database layer (`BEFORE UPDATE OR DELETE` trigger `trg_att_punch_log_immutable`).
- **`att_daily_record`** (derived): one row per user per logical work date — `work_date` (in the department's timezone, honoring `day_cutoff_hour`), `effective_clock_in`, `effective_clock_out`, `worked_minutes`, `status` (`NORMAL` | `LATE` | `EARLY_LEAVE` | `MISSING_PUNCH` | `ON_LEAVE` | `ABSENT`), `version` (`@Version`).
- An approved `PUNCH_CORRECTION` inserts a `CORRECTION`-sourced log row referencing the `att_request`, then recomputes the daily record. Original raw logs remain untouched for labor audits.
- Replaces the single `att_punch_record` table named in ADR-0003.

### 2. Multi-Region Calendars with Legal Day Types and TW/JP Templates

- `att_work_calendar` gains a **`calendar_code`** (e.g. `TW`, `JP`); `iam_department.calendar_code` binds each department to a calendar.
- **Day types:** `WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY` (休息日 / 所定休日), `STATUTORY_REST_DAY` (例假日 / 法定休日), `NATIONAL_HOLIDAY`.
- HR fully owns calendar editing. A **"Generate Year" action** pre-fills a year from a template, after which HR adjusts freely:
  - **TW template:** Sat = `REST_DAY`, Sun = `STATUTORY_REST_DAY`; fixed solar holidays; lunar holidays (Lunar New Year, Dragon Boat, Mid-Autumn) computed via a lunar-calendar library; observed-holiday shifting rules. Annually announced bridge days/makeup Saturdays (人事總處公告) are entered manually by HR.
  - **JP template:** Sat = `REST_DAY`, Sun = `STATUTORY_REST_DAY`; algorithmic holidays per the Cabinet Office rules (fixed dates, Happy Monday, vernal/autumnal equinox formula, 振替休日, 国民の休日).
- Templates are generated in Java (no external API dependency) and covered by unit tests against published holiday lists.

### 3. Daily Settlement Job & Daily Record Recomputation with Approved Requests

- A scheduled job runs per timezone after `day_cutoff_hour` (default `04:00`) to finalize the previous logical work date, producing `MISSING_PUNCH` / `ABSENT` statuses.
- Approved leave, overtime, or punch-correction requests trigger recomputation of only the affected dates via `AttendanceRecordCalculator.recomputeDailyRecord(userId, workDate, settledPass)`:
  - **Approved `LEAVE`:** aggregates daily approved `leaveMinutes`. If a leave interval covers the scheduled shift start (e.g. `09:00`–`13:00`), the lateness threshold shifts to the leave end time (`13:00`); if it covers the scheduled shift end (e.g. `14:00`–`18:00`), the early-leave threshold shifts to the leave start time (`14:00`). When `workedMinutes + leaveMinutes >= 480` with no lateness or early leave, `status` evaluates to `NORMAL` (or `ON_LEAVE` when `leaveMinutes >= 480`).
  - **Approved `OVERTIME`:** aggregates daily approved `overtimeMinutes` on `att_daily_record`. **Phase 3b counts the approved minutes as-is**; intersecting approved overtime with actual punch times is deferred.
  - **Approved `PUNCH_CORRECTION`:** appends an immutable `CORRECTION` row to `att_punch_log` referencing the request via the existing `correction_request_id` column (V3; V4 adds the foreign key to `att_request`), then invokes `recomputeDailyRecord`.

### 4. Leave Types, Minimum Units, Seniority-Based Annual Leave & FIFO Balance Ledger

- **`att_leave_type`**: trilingual name (`JSONB`), `min_unit_minutes` (e.g. 30, 60, 240 = half-day; configurable per type), paid flag, balance-tracked flag.
- **Duration calculation:** net minutes = requested interval ∩ working time of the calendar, minus the fixed 12:00–13:00 lunch break, rounded **up** to a multiple of `min_unit_minutes`.
- **FIFO balance deduction & exact audit trail (`att_leave_balance_tx`):** when an employee holds multiple active `att_leave_balance` buckets (e.g. deferred vs. current-year annual leave, or multiple comp-leave grants), deductions automatically follow **`valid_until ASC` (FIFO — earliest-expiring first)**. Each per-bucket freeze (`PENDING`), commit (`APPROVED`), or restore (`REJECTED` / `REVOKED`) is recorded in `att_leave_balance_tx` (`request_id`, `balance_id`, `tx_type`, `minutes`) so rejections and revocations restore 100% accurately to the original balance rows.
- **Single source of truth for comp leave:** every `att_comp_leave_grant` also creates one `att_leave_balance` bucket (`source_comp_grant_id` → grant). Comp-leave usage is tracked **only** in `att_leave_balance` / `att_leave_balance_tx`; the grant row never stores used minutes.
- **`att_leave_grant_rule`** (rule table, seeded per `calendar_code`):
  - **TW (Art. 38):** 6 mo–1 yr: 3 days; 1–2 yrs: 7; 2–3 yrs: 10; 3–5 yrs: 14; 5–10 yrs: 15; 10+ yrs: +1 day/yr up to 30. Grant basis column supports `ANNIVERSARY` (週年制) or `CALENDAR_YEAR` (曆年制); **Phase 3b implements the Anniversary Basis (`ANNIVERSARY`) algorithm** as the primary engine. Unused days at year end: pay-out or one-year deferral per agreement.
  - **JP (Art. 39):** 6 mo: 10 days; 1.5 yrs: 11; 2.5: 12; 3.5: 14; 4.5: 16; 5.5: 18; 6.5+: 20. Requires ≥ 80% attendance in the prior period; 2-year expiry. Hourly annual leave (時間単位年休) capped at 5 days/year. Tracks the mandatory 5-days-per-year utilization obligation (年5日取得義務) for employees granted ≥ 10 days.
- A scheduled grant job creates `att_leave_balance` rows automatically; HR may still grant manual/negotiated leave.
- **Prerequisite:** `iam_user.hire_date DATE` (see Decision 11).

### 5. Overtime Recording, Rate Rules & Caps

- Atria records **approved overtime minutes and their weighted equivalents**, never monetary amounts (no salary data is stored). Payroll systems consume the weighted-hour export (Phase 5).
- **`att_overtime_rate_rule`**: `calendar_code`, `day_type`, `from_minute`, `to_minute`, `multiplier` (`NUMERIC(6,4)`), `is_night_premium`. Seeded templates (editable by HR):
  - **TW:** practical HR standard multipliers — weekday overtime first 2 h **`×1.34`** (`1.3400`), from 3rd h **`×1.67`** (`1.6700`); rest-day work **`×1.34`** (first 2 h) / **`×1.67`** (3rd–8th h) / **`×2.67`** (9th–12th h); statutory rest day & national holiday **`×2.00`**.
    - *Known simplification:* under LSA Art. 39/40, work on a national holiday or statutory rest day within 8 hours is paid as one additional **day's** wage (per-day, not per-minute), hours beyond 8 fall under overtime rates, and statutory-rest-day work is only lawful under Art. 40 with a compensatory day off. The flat per-minute `×2.00` band is a modeling simplification to be confirmed by HR/legal before production use.
  - **JP:** overtime ×1.25; monthly overtime beyond 60 h ×1.50; statutory holiday ×1.35; late-night (22:00–05:00) +0.25 additive.
    - *Phase 3b engine scope:* the calculator evaluates only cumulative-minute bands (`from_minute` / `to_minute`). The late-night premium (clock-time based) and the > 60 h/month band (monthly-cumulative) are seeded for display but **not evaluated** until a later phase.
- **`att_overtime_cap_rule`**: configurable monthly / multi-month / yearly caps with `WARN` or `BLOCK` action at submission. Seeds: TW 46 h/month (54 h/month and 138 h/3 months with consent); JP Article 36 agreement 45 h/month & 360 h/year, special clause 720 h/year, < 100 h/month incl. holiday work, ≤ 80 h 2–6 month average, > 45 h at most 6 times/year.
- Each `OVERTIME` request chooses compensation: `PAY` or `COMP_LEAVE`.

### 6. Compensatory Leave Conversion (Configurable Ratio & Rounding)

- **`comp_leave_mode`** per `calendar_code` (or per rate rule):
  - `HOURS_1_TO_1` — statutory baseline: comp minutes = overtime minutes.
  - `RATE_MULTIPLIED` — company incentive (common practice): comp minutes = `CEILING(Σ(band minutes × band multiplier))` using `BigDecimal` arithmetic and **`RoundingMode.CEILING` to whole minutes** (employee-favorable rounding). Validation enforces an effective ratio ≥ 1.0.
- Every `att_comp_leave_grant` row stores its source `att_request`, the **original overtime minutes per rate band (`band_breakdown_json`)**, granted minutes, and `expires_at`; used / remaining minutes are read from its linked `att_leave_balance` bucket (Decision 4).
- **TW expiry / termination (Art. 32-1):** unused comp leave must be paid at overtime rates. The engine takes the unused fraction (bucket remaining ÷ granted minutes) and converts it back to *original* overtime minutes per band via `band_breakdown_json` (avoiding double-multiplication rounding drift) and emits a payable weighted-hour record.
- **JP:** standard 代休 offsets only the base 1.0 portion; the premium portion (e.g. 0.25) is recorded as residual payable premium regardless of mode, unless HR configures 代替休暇 for the > 60 h/month portion under a labor-management agreement.

### 7. Monthly Closing with Pending Requests

- Closing a month is **blocked while any `PENDING` request touches that month**; the API returns the list of blocking requests for HR to resolve. No automatic rejection.
- Scheduled auto-close follows the same rule and notifies HR when blocked.

### 8. Monthly Leave Balance Digest & Phase 3b/4 Sequencing

- **Phase 3b scope:** builds the **Leave Balance, 90-Day Expiring Comp-Leave, and JP 5-Day Obligation Progress query service and `/portal` dashboard API** so employees and HR have real-time visibility immediately.
- **Phase 4 scope (`LeaveBalanceDigestJob`):** once the Phase 4 notification foundation (`ntf_*` tables and `EmailNotificationChannel`, see Decision 10) lands, the scheduled monthly digest job runs on day 1 at 09:00 per department timezone to deliver the summary via email and in-app notification (idempotent per `(user_id, period)`).

### 9. Phase 3 ↔ Phase 4 Approval Contract & Unidirectional Dependency

- To uphold the ArchUnit cycle-free module rule (`ModularArchitectureTest`), **all attendance request domain events reside in `io.github.skr416.atria.attendance.event`**: `AttRequestSubmittedEvent`, `AttRequestCancelledEvent`, `AttRequestApprovedEvent`, `AttRequestRejectedEvent`, and `AttRequestRevokedEvent`.
- **Unidirectional module flow (`workflow` ➔ `attendance`):** `attendance` never depends on `workflow`. `attendance` publishes `AttRequestSubmittedEvent` on submission and `AttRequestCancelledEvent` on applicant withdrawal (so the workflow can close its instance), and listens for `Approved` / `Rejected` / `Revoked` events to execute balance freeze/commit/release (`att_leave_balance_tx`), `att_punch_log(CORRECTION)` insertion, and `att_daily_record` recomputation.
- **`att_request` state machine** (`version` column for optimistic locking):
  - `PENDING` → `APPROVED` | `REJECTED` (approval outcome events).
  - `PENDING` → `CANCELLED` (applicant withdraws; frozen balance is released).
  - `APPROVED` → `REVOKED` (post-approval revocation; balance restored and affected dates recomputed).
  - `REJECTED`, `CANCELLED`, and `REVOKED` are terminal.
  - **Guards** (enforced by a public `attendance` guard service at the submit / withdraw / revoke entry points; the Phase 4 workflow calls it before opening a revocation flow): any request touching a closed month is rejected with `ATT_MONTHLY_PERIOD_CLOSED` (2005) until HR reopens the month; an `OVERTIME` request with `COMP_LEAVE` cannot be revoked once its comp-leave bucket has used or pending minutes.
- Phase 3b ships a **dev/test-only direct approval stub controller** inside `attendance` (restricted via `@Profile({"local", "pgtest"})`, the profiles used by local runs and the H2 / PostgreSQL test suites) that publishes those same events to exercise the full loop prior to Phase 4.

### 10. Workflow Engine Refinements (Phase 4)

- **Approver fallback:** if a node's resolved leader is missing (no `leader_id`, or the top of the tree is reached), walk up the department tree; if none is found, assign to the `HR` role.
- **Conditions** in `wf_flow_node_condition` are structured rows (`field_key`, `operator`, `value`, logical group) evaluated in Java. No SpEL or free-form expression strings (prevents expression injection; enables a card-based HR UI).
- **Countersign / parallel approval (會簽) is explicitly out of scope.**
- **Notification foundation moves into Phase 4:** a `NotificationChannel` abstraction, in-app notifications, `EmailNotificationChannel`, and a delivery log (new table prefix `ntf_`). Phase 5 retains the webhook bot channel and announcements. The monthly digest (Decision 8) is enabled once this foundation lands.

### 11. `iam_user.hire_date`

- Added via Flyway `V2__add_user_hire_date.sql` (PostgreSQL & H2) as a nullable `DATE` (a calendar date, not a UTC timestamp). Nullable to accommodate system accounts; the admin UI requires it for employees. The leave grant job skips users without `hire_date` and reports them to HR.
- Phase 3 migrations therefore start at `V3`.

### 12. PostgreSQL as Test Target

- A locally installed PostgreSQL 16 becomes the **single source of truth and the integration test target** (dedicated `atria_test` database). PostgreSQL-specific features (`JSONB` operators, partial unique indexes) are permitted.
- The H2 migration tree is retained through Phase 3 for zero-install demos. At Phase 4 kickoff, re-evaluate replacing H2 with a Docker Compose demo stack.

### 13. Phase 3 Split

| Sub-phase | Target Tag | Scope |
| :--- | :--- | :--- |
| **3a — Attendance Core** | `v0.4.0-attendance-core` | Calendar + TW/JP templates, `att_punch_log` / `att_daily_record`, punch rules & cutoff, bot punch, daily settlement job, monthly closing, `/portal` clock-in card & calendar, `/admin` calendar UI |
| **3b — Leave & Overtime** | `v0.4.5-leave-overtime` | Leave types & minimum units, grant rules & job, leave calculator, balance ledger, overtime rate/cap rules, comp leave ledger & expiry payout, approval event contract + test stub |

## Deferred (Explicitly Not Now)

- Per-employee shift rosters (extension point `WorkScheduleResolver` remains).
- Automatic import of government-announced bridge days.
- GPS-based punch restriction (an optional IP allowlist may be added in 3a).
- Deployment topology (the CI pipeline was delivered in Phase 3a.5).
- Intersecting approved overtime with actual punch times (Phase 3b counts approved minutes as-is).
- Evaluating the JP late-night premium and the > 60 h/month overtime band (seeded only in Phase 3b).

## Consequences

- Labor-audit integrity is preserved by construction: raw punches are never mutated.
- Statutory rules for two jurisdictions are data, not code; adding a region means seeding rule rows and a calendar template.
- Phase 3 and Phase 4 can be developed and tested independently through the event contract.
- `ROADMAP.md`, `ARCHITECTURE.md`, and ADR-0003/ADR-0005 table naming (`att_punch_record` → `att_punch_log` + `att_daily_record`) have been aligned upon Phase 2 completion.
