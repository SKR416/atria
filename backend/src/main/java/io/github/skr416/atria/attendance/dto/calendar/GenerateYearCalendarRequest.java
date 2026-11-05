package io.github.skr416.atria.attendance.dto.calendar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for HR to generate a full year of calendar dates from built-in TW/JP templates
 * (ADR-0008 Decision 2).
 */
public record GenerateYearCalendarRequest(
        @NotBlank(message = "Calendar code is required (e.g. TW, JP)")
        String calendarCode,

        @Min(value = 2000, message = "Year must be >= 2000")
        @Max(value = 2100, message = "Year must be <= 2100")
        int year,

        boolean overwriteExisting
) {
}
