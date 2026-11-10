package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.AttendanceRequest;

import java.time.LocalDate;

/**
 * Guard service enforcing monthly close locks, retroactive application limits, and compensatory-leave
 * revocation constraints at submit / withdraw / revoke entry points (ADR-0008 Decision 9).
 */
public interface AttRequestGuard {

    /**
     * Verifies that every {@code YearMonth} spanned by {@code [startDate, endDate]} is open for {@code calendarCode}.
     * Throws {@code ATT_MONTHLY_PERIOD_CLOSED} (2005) if any touched month is locked.
     */
    void assertMonthOpen(String calendarCode, LocalDate startDate, LocalDate endDate);

    /**
     * Verifies that {@code targetDate} does not violate the regional {@code maxRetroactiveDays} limit relative to {@code today}.
     * Throws {@code ATT_RETROACTIVE_LIMIT_EXCEEDED} (2006) if overdue.
     */
    void assertRetroactiveLimit(String calendarCode, LocalDate targetDate, LocalDate today);

    /**
     * Verifies that an {@code APPROVED} request can be safely transitioned to {@code REVOKED}:
     * 1. Touched months must not be closed ({@code ATT_MONTHLY_PERIOD_CLOSED} = 2005).
     * 2. If the request is an {@code OVERTIME} request with {@code COMP_LEAVE}, its granted comp-leave bucket
     *    must have {@code usedMinutes == 0 && pendingMinutes == 0} ({@code ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE} = 2025).
     */
    void assertCanRevokeApprovedRequest(AttendanceRequest request);
}
