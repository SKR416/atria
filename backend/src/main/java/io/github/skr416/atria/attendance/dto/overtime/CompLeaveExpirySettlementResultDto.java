package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.OvertimeBandBreakdownItem;

import java.util.List;

/**
 * Result of settling an expired compensatory leave grant under Taiwan LSA Art. 32-1,
 * converting the unused fraction back to original overtime band minutes and weighted hours (ADR-0008 Decision 6).
 */
public record CompLeaveExpirySettlementResultDto(
        Long compGrantId,
        Long balanceId,
        Long userId,
        int grantedMinutes,
        int usedMinutes,
        int unusedMinutes,
        int restoredOriginalOvertimeMinutes,
        int payableWeightedMinutes,
        List<OvertimeBandBreakdownItem> restoredBands
) {
}
