package io.github.skr416.atria.attendance.dto.leave;

import java.time.LocalDate;

/**
 * Japan Labor Standards Act Art. 39(7) mandatory 5-days-per-year (2400 minutes) annual leave utilization progress
 * for employees granted 10 or more annual leave days (ADR-0008 Decisions 4 & 8).
 */
public record JpFiveDayObligationDto(
        boolean applicable,
        int targetMinutes,
        int usedMinutes,
        int pendingMinutes,
        int remainingObligationMinutes,
        LocalDate deadlineDate,
        boolean fulfilled
) {
}
