package io.github.skr416.atria.attendance.dto.calendar;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import io.github.skr416.atria.iam.dto.dept.DepartmentTreeNodeResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarDayResponse {

    private Long id;
    private String calendarCode;
    private LocalDate workDate;
    private CalendarDayType dayType;
    private boolean workingDay;
    private LocalTime workStartTime;
    private LocalTime workEndTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private Map<String, String> holidayName;
    private String localizedHolidayName;
    private String note;

    public static WorkCalendarDayResponse from(WorkCalendarDay entity, Locale locale) {
        String localized = entity.getHolidayName() != null
                ? DepartmentTreeNodeResponse.resolveLocalizedName(entity.getHolidayName(), locale)
                : null;
        return WorkCalendarDayResponse.builder()
                .id(entity.getId())
                .calendarCode(entity.getCalendarCode())
                .workDate(entity.getWorkDate())
                .dayType(entity.getDayType())
                .workingDay(entity.getDayType() != null && entity.getDayType().isWorkingDay())
                .workStartTime(entity.getWorkStartTime())
                .workEndTime(entity.getWorkEndTime())
                .breakStartTime(entity.getBreakStartTime())
                .breakEndTime(entity.getBreakEndTime())
                .holidayName(entity.getHolidayName())
                .localizedHolidayName(localized)
                .note(entity.getNote())
                .build();
    }
}
