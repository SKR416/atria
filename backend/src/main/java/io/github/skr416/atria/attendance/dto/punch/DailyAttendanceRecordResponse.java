package io.github.skr416.atria.attendance.dto.punch;

import io.github.skr416.atria.attendance.model.AttendanceStatus;
import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAttendanceRecordResponse {

    private Long id;
    private Long userId;
    private Long departmentId;
    private String calendarCode;
    private LocalDate workDate;
    private CalendarDayType dayType;
    private LocalTime scheduledStartTime;
    private LocalTime scheduledEndTime;
    private Instant firstRawClockIn;
    private Instant lastRawClockOut;
    private Instant effectiveClockIn;
    private Instant effectiveClockOut;
    private int workedMinutes;
    private int lateMinutes;
    private int earlyLeaveMinutes;
    private int leaveMinutes;
    private int overtimeMinutes;
    private AttendanceStatus status;
    private int punchCount;
    private boolean settled;
    private String note;
    private Instant updatedAt;

    public static DailyAttendanceRecordResponse from(DailyAttendanceRecord entity) {
        if (entity == null) {
            return null;
        }
        return DailyAttendanceRecordResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .departmentId(entity.getDepartmentId())
                .calendarCode(entity.getCalendarCode())
                .workDate(entity.getWorkDate())
                .dayType(entity.getDayType())
                .scheduledStartTime(entity.getScheduledStartTime())
                .scheduledEndTime(entity.getScheduledEndTime())
                .firstRawClockIn(entity.getFirstRawClockIn())
                .lastRawClockOut(entity.getLastRawClockOut())
                .effectiveClockIn(entity.getEffectiveClockIn())
                .effectiveClockOut(entity.getEffectiveClockOut())
                .workedMinutes(entity.getWorkedMinutes())
                .lateMinutes(entity.getLateMinutes())
                .earlyLeaveMinutes(entity.getEarlyLeaveMinutes())
                .leaveMinutes(entity.getLeaveMinutes())
                .overtimeMinutes(entity.getOvertimeMinutes())
                .status(entity.getStatus())
                .punchCount(entity.getPunchCount())
                .settled(entity.isSettled())
                .note(entity.getNote())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
