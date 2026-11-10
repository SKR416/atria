package io.github.skr416.atria.attendance.dto.leave;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record LeaveTypeUpsertRequest(
        @NotBlank String code,
        @NotEmpty Map<String, String> name,
        @Min(1) int minUnitMinutes,
        boolean paid,
        boolean balanceTracked,
        boolean attachmentRequired,
        boolean enabled,
        int sortOrder,
        String description
) {
}
