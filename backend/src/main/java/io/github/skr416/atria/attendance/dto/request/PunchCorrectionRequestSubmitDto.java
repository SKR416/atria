package io.github.skr416.atria.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record PunchCorrectionRequestSubmitDto(
        @NotNull LocalDate workDate,
        @NotNull Instant correctedPunchTime,
        @NotBlank String reason,
        String attachmentPath
) {
}
