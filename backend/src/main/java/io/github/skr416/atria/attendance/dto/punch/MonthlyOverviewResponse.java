package io.github.skr416.atria.attendance.dto.punch;

import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Combined monthly overview for the employee portal calendar view:
 * includes regional work calendar days, derived daily attendance records, raw punches, and lock status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyOverviewResponse {

    private Long userId;
    private String calendarCode;
    private String timezone;
    private String yearMonth;
    private boolean monthlyClosed;
    private int totalWorkedMinutes;
    private int normalDays;
    private int lateCount;
    private int earlyLeaveCount;
    private int missingPunchCount;
    private int absentCount;
    private List<WorkCalendarDayResponse> calendarDays;
    private List<DailyAttendanceRecordResponse> dailyRecords;
    private List<PunchLogResponse> punchLogs;
}
