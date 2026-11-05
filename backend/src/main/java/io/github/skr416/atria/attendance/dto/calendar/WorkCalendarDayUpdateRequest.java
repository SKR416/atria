package io.github.skr416.atria.attendance.dto.calendar;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.Map;

/**
 * Request payload for HR to create or update a single date in {@code att_work_calendar}.
 */
public record WorkCalendarDayUpdateRequest(
        @NotNull(message = "Day type is required")
        CalendarDayType dayType,

        LocalTime workStartTime,
        LocalTime workEndTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        Map<String, String> holidayName,

        @Size(max = 255)
        String note
) {
}
