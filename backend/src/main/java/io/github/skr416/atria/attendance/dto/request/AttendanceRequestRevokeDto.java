package io.github.skr416.atria.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AttendanceRequestRevokeDto(
        @NotBlank String reason
) {
}
