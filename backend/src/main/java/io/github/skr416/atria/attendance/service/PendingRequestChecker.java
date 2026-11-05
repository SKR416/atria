package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.close.BlockingPendingRequestDto;

import java.time.YearMonth;
import java.util.List;

/**
 * Extension contract checked by {@code MonthlyCloseService} before locking a month (ADR-0008 Decision 7).
 * Phase 3b's {@code att_request} repository will provide a concrete bean implementation;
 * tests in Phase 3a can also register or mock this bean to verify pending-request blocking.
 */
public interface PendingRequestChecker {

    /**
     * Returns any {@code PENDING} attendance requests touching the given calendar region and year-month.
     */
    List<BlockingPendingRequestDto> findPendingRequestsForMonth(String calendarCode, YearMonth yearMonth);
}
