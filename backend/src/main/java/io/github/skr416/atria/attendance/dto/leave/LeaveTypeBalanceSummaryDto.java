package io.github.skr416.atria.attendance.dto.leave;

import java.util.List;

public record LeaveTypeBalanceSummaryDto(
        String leaveTypeCode,
        String localizedLeaveTypeName,
        int minUnitMinutes,
        boolean paid,
        boolean balanceTracked,
        int totalMinutes,
        int usedMinutes,
        int pendingMinutes,
        int availableMinutes,
        List<LeaveBalanceBucketDto> buckets
) {
}
