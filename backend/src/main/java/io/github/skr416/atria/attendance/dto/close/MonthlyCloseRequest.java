package io.github.skr416.atria.attendance.dto.close;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for HR to execute monthly closing lock on a calendar region and {@code YYYY-MM}.
 */
public record MonthlyCloseRequest(
        @NotBlank(message = "Calendar code is required")
        String calendarCode,

        @NotBlank(message = "Year-Month is required")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Year-Month must match YYYY-MM")
        String yearMonth,

        @Size(max = 500)
        String note
) {
}
