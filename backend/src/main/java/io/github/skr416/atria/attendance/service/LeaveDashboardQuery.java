package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.LeaveBalanceBucketDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveDashboardResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-query contract for employee leave balance summaries, 90-day expiring compensatory leave alerts,
 * and Japan Labor Standards Act Art. 39(7) 5-day mandatory annual leave obligation progress
 * (ADR-0008 Decisions 4 & 8).
 */
public interface LeaveDashboardQuery {

    /**
     * Builds the comprehensive leave dashboard for {@code userId} as of {@code asOfDate}.
     */
    LeaveDashboardResponse getDashboard(Long userId, LocalDate asOfDate);

    /**
     * Lists all active and historical leave balance buckets for {@code userId} ordered by {@code valid_until ASC}.
     */
    List<LeaveBalanceBucketDto> listUserBuckets(Long userId);
}
