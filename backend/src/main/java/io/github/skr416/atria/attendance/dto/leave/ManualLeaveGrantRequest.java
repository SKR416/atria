package io.github.skr416.atria.attendance.dto.leave;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ManualLeaveGrantRequest(
        @NotNull Long userId,
        @NotBlank String leaveTypeCode,
        @Min(1) int grantMinutes,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        String note
) {
}
