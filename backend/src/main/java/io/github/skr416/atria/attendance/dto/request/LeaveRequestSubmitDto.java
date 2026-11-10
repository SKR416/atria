package io.github.skr416.atria.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LeaveRequestSubmitDto(
        @NotBlank String leaveTypeCode,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank String reason,
        String attachmentPath
) {
}
