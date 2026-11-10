package io.github.skr416.atria.attendance.dto.leave;

import java.time.LocalDate;

public record ExpiringCompLeaveDto(
        Long balanceId,
        Long compGrantId,
        LocalDate workDate,
        int remainingMinutes,
        LocalDate expiresAt,
        long daysUntilExpiry
) {
}
