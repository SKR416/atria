# ADR-0003: Three-Hall Routing and Calendar-Driven Attendance Model

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Early planning considered a full shift-scheduling engine (rotating shifts, overnight shifts, per-employee rosters) and a single monolithic admin sidebar for all users. Upon review:

1. **Shift Scheduling Complexity:** Most standard office and enterprise environments operate on a unified corporate calendar defined by HR rather than per-employee rotating shift rosters. Building a complex shift-scheduling engine upfront delays the core attendance-to-approval loop.
2. **Role UX Separation:** Regular employees only need a clean daily entry point to clock in/out, inspect the corporate work calendar, submit leave or punch-correction requests, read internal announcements, and use AI tools. Exposing a complex management console to every employee degrades usability.

## Decision

1. **Calendar-Driven Attendance Model (`att_work_calendar`):**
   - Instead of individual shift scheduling, HR defines the company-wide work calendar (`WORKDAY`, `HOLIDAY`, `MAKEUP_DAY`) along with standard start/end times (e.g., `09:00`–`18:00`).
   - A `WorkScheduleResolver` interface resolves expected working hours for any user and date. The initial implementation (`CalendarBasedScheduleResolver`) reads `att_work_calendar`, keeping an extension point open if per-employee shift rosters are ever needed later.
2. **Closed-Loop Clock-In, Leave, and Punch Correction:**
   - Daily clock-in/out records (`att_punch_record`) are evaluated against `att_work_calendar` into explicit statuses: `NORMAL`, `LATE`, `EARLY_LEAVE`, `MISSING_PUNCH`, `ON_LEAVE`, or `ABSENT`.
   - Both leave applications and missed-punch corrections share the `att_request` table differentiated by `request_type` (`LEAVE` | `PUNCH_CORRECTION`). When approved through the workflow engine, the corresponding `att_punch_record` is updated automatically.
3. **Three-Hall Frontend Architecture:**
   - `/public`: Unauthenticated external announcement site.
   - `/portal`: Clean, calendar-centric Employee Self-Service (ESS) portal for clock-in/out, leave/punch-correction requests, internal announcements, and AI tools.
   - `/admin`: Management console restricted to department leaders, HR, and administrators, featuring a real-time team attendance dashboard on the home screen.

## Consequences

- Implementation scope for Phase 3 is substantially leaner and delivers an end-to-end attendance + approval workflow faster.
- Route guards in Vue Router and role/data-scope checks in Spring Security cleanly separate regular employee self-service (`/portal`) from managerial operations (`/admin`).
