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

- **`att_punch_log`** (append-only): one row per punch event — `user_id`, `raw_punch_time` (UTC, second precision), `client_ip`, `source` (`WEB` | `BOT` | `CORRECTION`), `bot_client_id`. Rows are never updated or deleted.
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

### 3. Daily Settlement Job

- A scheduled job runs per timezone after `day_cutoff_hour` (default `04:00`) to finalize the previous logical work date, producing `MISSING_PUNCH` / `ABSENT` statuses.
- Approved leave or punch-correction requests trigger recomputation of only the affected dates.

### 4. Leave Types, Minimum Units & Seniority-Based Annual Leave

- **`att_leave_type`**: trilingual name (`JSONB`), `min_unit_minutes` (e.g. 30, 60, 240 = half-day; configurable per type), paid flag, balance-tracked flag.
- **Duration calculation:** net minutes = requested interval ∩ working time of the calendar, minus the fixed 12:00–13:00 lunch break, rounded **up** to a multiple of `min_unit_minutes`.
- **`att_leave_grant_rule`** (rule table, seeded per `calendar_code`):
  - **TW (Art. 38):** 6 mo–1 yr: 3 days; 1–2 yrs: 7; 2–3 yrs: 10; 3–5 yrs: 14; 5–10 yrs: 15; 10+ yrs: +1 day/yr up to 30. Grant basis configurable: anniversary (週年制) or calendar year (曆年制). Unused days at year end: pay-out or one-year deferral per agreement.
  - **JP (Art. 39):** 6 mo: 10 days; 1.5 yrs: 11; 2.5: 12; 3.5: 14; 4.5: 16; 5.5: 18; 6.5+: 20. Requires ≥ 80% attendance in the prior period; 2-year expiry. Hourly annual leave (時間単位年休) capped at 5 days/year. Tracks the mandatory 5-days-per-year utilization obligation (年5日取得義務) for employees granted ≥ 10 days.
- A scheduled grant job creates `att_leave_balance` rows automatically; HR may still grant manual/negotiated leave.
- **Prerequisite:** `iam_user.hire_date DATE` (see Decision 11).

### 5. Overtime Recording, Rate Rules & Caps

- Atria records **approved overtime minutes and their weighted equivalents**, never monetary amounts (no salary data is stored). Payroll systems consume the weighted-hour export (Phase 5).
- **`att_overtime_rate_rule`**: `calendar_code`, `day_type`, `from_minute`, `to_minute`, `multiplier`, `is_night_premium`. Seeded templates (editable by HR):
  - **TW:** weekday overtime first 2 h ×1⅓, from 3rd h ×1⅔; rest-day work ×1⅓ / ×1⅔ banding; statutory rest day & national holiday ×2.
  - **JP:** overtime ×1.25; monthly overtime beyond 60 h ×1.50; statutory holiday ×1.35; late-night (22:00–05:00) +0.25 additive.
- **`att_overtime_cap_rule`**: configurable monthly / multi-month / yearly caps with `WARN` or `BLOCK` action at submission. Seeds: TW 46 h/month (54 h/month and 138 h/3 months with consent); JP Article 36 agreement 45 h/month & 360 h/year, special clause 720 h/year, < 100 h/month incl. holiday work, ≤ 80 h 2–6 month average, > 45 h at most 6 times/year.
- Each `OVERTIME` request chooses compensation: `PAY` or `COMP_LEAVE`.

### 6. Compensatory Leave Conversion (Configurable Ratio)

- **`comp_leave_mode`** per `calendar_code` (or per rate rule):
  - `HOURS_1_TO_1` — statutory baseline: comp minutes = overtime minutes.
  - `RATE_MULTIPLIED` — company incentive (common practice): comp minutes = Σ(band minutes × band `multiplier`). Validation enforces an effective ratio ≥ 1.0.
- Every comp-leave grant row stores its source `att_request`, the **original overtime minutes per rate band**, granted minutes, and `expires_at`.
- **TW expiry / termination (Art. 32-1):** unused comp leave must be paid at overtime rates. The engine converts the unused fraction back to *original* overtime minutes per band (not inflated comp minutes) and emits a payable weighted-hour record.
- **JP:** standard 代休 offsets only the base 1.0 portion; the premium portion (e.g. 0.25) is recorded as residual payable premium regardless of mode, unless HR configures 代替休暇 for the > 60 h/month portion under a labor-management agreement.

### 7. Monthly Closing with Pending Requests

- Closing a month is **blocked while any `PENDING` request touches that month**; the API returns the list of blocking requests for HR to resolve. No automatic rejection.
- Scheduled auto-close follows the same rule and notifies HR when blocked.

### 8. Monthly Leave Balance Digest (Email)

- **`LeaveBalanceDigestJob`** runs on a configurable day/time each month (default: day 1, 09:00 in the department's timezone) and sends each employee, in their `locale`:
  - remaining hours per leave type,
  - balances expiring within a configurable window (default 90 days), including comp leave,
  - JP: progress toward the 5-day annual leave obligation.
- Delivered via `EmailNotificationChannel` with an in-app copy. Idempotent per `(user_id, period)` via a delivery log. Dev profile writes emails to log/file (no SMTP required).
- Managers receive a team summary of members at risk of expiry or obligation shortfall.

### 9. Phase 3 ↔ Phase 4 Approval Contract

- Phase 3 defines domain events **`AttRequestApprovedEvent`** / **`AttRequestRejectedEvent`** / **`AttRequestRevokedEvent`** and implements all write-back logic (balance freeze/commit/release, daily-record recompute) against them.
- Phase 3 ships a **test-only direct approval endpoint** (restricted to dev/test profiles) to exercise the full loop. Phase 4's workflow engine publishes the same events, replacing the stub without touching attendance logic.

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

| Sub-phase | Scope |
| :--- | :--- |
| **3a — Attendance Core** | Calendar + TW/JP templates, `att_punch_log` / `att_daily_record`, punch rules & cutoff, bot punch, daily settlement job, monthly closing, `/portal` clock-in card & calendar, `/admin` calendar UI |
| **3b — Leave & Overtime** | Leave types & minimum units, grant rules & job, leave calculator, balance ledger, overtime rate/cap rules, comp leave ledger & expiry payout, approval event contract + test stub |

Exact Git tags are finalized when `ROADMAP.md` is updated.

## Deferred (Explicitly Not Now)

- Per-employee shift rosters (extension point `WorkScheduleResolver` remains).
- Automatic import of government-announced bridge days.
- GPS-based punch restriction (an optional IP allowlist may be added in 3a).
- CI pipeline and deployment topology.

## Consequences

- Labor-audit integrity is preserved by construction: raw punches are never mutated.
- Statutory rules for two jurisdictions are data, not code; adding a region means seeding rule rows and a calendar template.
- Phase 3 and Phase 4 can be developed and tested independently through the event contract.
- `ROADMAP.md`, `ARCHITECTURE.md`, and ADR-0003 table naming (`att_punch_record` → `att_punch_log` + `att_daily_record`) must be updated after Phase 2 completes.
