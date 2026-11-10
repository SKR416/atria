package io.github.skr416.atria.attendance.dto.request;

import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import io.github.skr416.atria.attendance.model.OvertimeCompensationType;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceRequestResponse(
        Long id,
        Long userId,
        String username,
        String displayName,
        Long departmentId,
        String calendarCode,
        AttendanceRequestType requestType,
        AttendanceRequestStatus status,
        String leaveTypeCode,
        String localizedLeaveTypeName,
        LocalDate startDate,
        LocalDate endDate,
        Instant startTime,
        Instant endTime,
        int requestedMinutes,
        int weightedMinutes,
        OvertimeCompensationType overtimeCompType,
        CompLeaveConversionMode compLeaveMode,
        String reason,
        String attachmentPath,
        String warningMessages,
        Long reviewedBy,
        Instant reviewedAt,
        String reviewComment,
        Long revokedBy,
        Instant revokedAt,
        String revokeReason,
        Instant createdAt
) {
}
