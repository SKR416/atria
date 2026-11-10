package io.github.skr416.atria.attendance.dto.leave;

import io.github.skr416.atria.attendance.model.LeaveBalance;

import java.time.LocalDate;

public record LeaveBalanceBucketDto(
        Long id,
        Long userId,
        String leaveTypeCode,
        String localizedLeaveTypeName,
        String calendarCode,
        int grantYear,
        int totalMinutes,
        int usedMinutes,
        int pendingMinutes,
        int availableMinutes,
        LocalDate validFrom,
        LocalDate validUntil,
        Long sourceCompGrantId,
        int mandatoryTargetMinutes,
        String note
) {
    public static LeaveBalanceBucketDto from(LeaveBalance b, String localizedLeaveTypeName) {
        return new LeaveBalanceBucketDto(
                b.getId(),
                b.getUserId(),
                b.getLeaveTypeCode(),
                localizedLeaveTypeName,
                b.getCalendarCode(),
                b.getGrantYear(),
                b.getTotalMinutes(),
                b.getUsedMinutes(),
                b.getPendingMinutes(),
                b.getAvailableMinutes(),
                b.getValidFrom(),
                b.getValidUntil(),
                b.getSourceCompGrantId(),
                b.getMandatoryTargetMinutes(),
                b.getNote()
        );
    }
}
