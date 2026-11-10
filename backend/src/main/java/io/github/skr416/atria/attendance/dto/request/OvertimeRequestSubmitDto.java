package io.github.skr416.atria.attendance.dto.request;

import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import io.github.skr416.atria.attendance.model.OvertimeCompensationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record OvertimeRequestSubmitDto(
        @NotNull LocalDate workDate,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull OvertimeCompensationType overtimeCompType,
        CompLeaveConversionMode compLeaveMode,
        @NotBlank String reason
) {
}
