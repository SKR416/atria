package io.github.skr416.atria.attendance.dto.punch;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Real-time summary of the employee's current logical work date, localized timezone,
 * derived daily record, and raw punch logs for the {@code /portal} clock-in card.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayAttendanceSummaryResponse {

    private Long userId;
    private Long departmentId;
    private String calendarCode;
    private String timezone;
    private Instant serverUtcNow;
    private LocalDate logicalWorkDate;
    private int dayCutoffHour;
    private CalendarDayType dayType;
    private boolean workingDay;
    private String localizedHolidayName;
    private LocalTime scheduledStartTime;
    private LocalTime scheduledEndTime;
    private boolean monthlyPeriodClosed;
    private DailyAttendanceRecordResponse dailyRecord;
    private List<PunchLogResponse> punchLogs;
}
