# ADR-0003: Three-Hall Routing and Calendar-Driven Attendance Model

- **Status:** Accepted (refined by [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md))
- **Date:** 2026-10-28

## Context

Early planning considered a full shift-scheduling engine (rotating shifts, overnight shifts, per-employee rosters) and a single monolithic admin sidebar for all users. Upon review:

1. **Shift Scheduling Complexity:** Most standard office and enterprise environments operate on a unified corporate calendar defined by HR rather than per-employee rotating shift rosters. Building a complex shift-scheduling engine upfront delays the core attendance-to-approval loop.
2. **Role UX Separation:** Regular employees only need a clean daily entry point to clock in/out, inspect the corporate work calendar, submit leave or punch-correction requests, read internal announcements, and use AI tools. Exposing a complex management console to every employee degrades usability.

## Decision

1. **Calendar-Driven Attendance Model & Multi-Timezone Cutoff (`att_work_calendar`):**
   - Instead of individual shift scheduling, HR defines the regional work calendar (`calendar_code` such as `TW` or `JP`, with legal day types `WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY`, `STATUTORY_REST_DAY`, `NATIONAL_HOLIDAY` per [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md)) along with standard start/end times (e.g., `09:00`–`18:00`).
   - Departments and branch offices define their localized timezone (`iam_department.timezone`, e.g. `Asia/Taipei`, `Asia/Tokyo`) and bound calendar (`iam_department.calendar_code`). Work dates and cutoffs are evaluated against the employee's department timezone while all database timestamps remain in UTC.
   - A `WorkScheduleResolver` interface resolves expected working hours for any user and date. The initial implementation (`CalendarBasedScheduleResolver`) reads `att_work_calendar`, keeping an extension point open if per-employee shift rosters are ever needed later.
2. **Split Punch Tables (`att_punch_log` + `att_daily_record`) & Closed-Loop Attendance (refined by [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md)):**
   - Punch data is split into two tables to preserve labor-audit immutability while supporting daily derivation and corrections:
     - `att_punch_log` (append-only): Records each raw punch event (`raw_punch_time` in UTC down to the exact second, `client_ip`, `source` `WEB` | `BOT` | `CORRECTION`, `bot_client_id`). Rows are strictly immutable.
     - `att_daily_record` (derived): Stores one row per user per logical `work_date` with `effective_clock_in` / `effective_clock_out` (calculated via `att_punch_rule` grace periods and rounding intervals), `worked_minutes`, `status`, and `@Version`.
   - An authorized third-party bot punch endpoint is exposed to permit validated proxies (e.g., company Slack/Teams/LINE bot) to submit clock-ins.
   - Break calculation follows a standard fixed deduction rule: shifts intersecting 12:00–13:00 automatically deduct 1 hour of unpaid break time.
   - Overtime tracking is strictly approval-based: clock-out times past the scheduled end time do not automatically generate overtime hours; recognized overtime requires an approved `OVERTIME` workflow request, maintaining compliance while accommodating managerial negotiations (e.g., converting overtime to compensatory leave).
   - Daily records are evaluated against `att_work_calendar` into explicit statuses: `NORMAL`, `LATE`, `EARLY_LEAVE`, `MISSING_PUNCH`, `ON_LEAVE`, or `ABSENT`.
   - **Cross-Midnight Day Cutoff (`day_cutoff_hour`):** `att_punch_rule` defines a logical day cutoff hour (default `04:00` AM) so late-night overtime clock-outs past midnight are accurately attributed to the previous business workday.
   - **Bot Endpoint Anti-Replay HMAC Security:** Third-party Bot clock-in requests are authenticated via `HMAC-SHA256` signature verification paired with a ±5 minute timestamp window (`HmacSignatureVerifier`) and real client IP resolution (`ClientIpResolver`).
   - **Leave Balance Ledger (`att_leave_balance`):** Tracks `total_hours`, `used_hours`, `pending_hours` (frozen upon submission, committed on final approval, released on rejection/return/revocation), and `expires_at`, with an HR interface for granting negotiated compensatory leave.
   - **Monthly Closing Lock (`att_monthly_close`) & Overdue Retroactive Blocking:** Supports manual HR monthly lock and scheduled auto-closing (e.g., locking the prior month on a configured day of the new month) to prohibit retroactive work-hour or leave modifications after payroll settlement; closing is blocked while any `PENDING` request touches that month. Retroactive leave or punch correction requests exceeding the configured deadline (`max_retroactive_days`) are directly blocked at submission.
   - Leave applications, missed-punch corrections, and overtime requests share the `att_request` table differentiated by `request_type` (`LEAVE` | `PUNCH_CORRECTION` | `OVERTIME`). When approved (via `AttRequestApprovedEvent`), a `PUNCH_CORRECTION` appends a `CORRECTION` row to `att_punch_log` and recomputes `att_daily_record` in the same transaction without mutating historical raw punches.
3. **Three-Hall Frontend Architecture:**
   - `/public`: Unauthenticated external announcement site.
   - `/portal`: Clean, calendar-centric Employee Self-Service (ESS) portal for clock-in/out, leave/punch-correction requests, internal announcements, and AI tools.
   - `/admin`: Management console restricted to department leaders, HR, and administrators, featuring a real-time team attendance dashboard on the home screen.

## Consequences

- Implementation scope for Phase 3 is substantially leaner and delivers an end-to-end attendance + approval workflow faster.
- Route guards in Vue Router and role/data-scope checks in Spring Security cleanly separate regular employee self-service (`/portal`) from managerial operations (`/admin`).
