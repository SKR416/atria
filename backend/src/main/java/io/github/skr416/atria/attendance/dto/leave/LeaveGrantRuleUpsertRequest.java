package io.github.skr416.atria.attendance.dto.leave;

import io.github.skr416.atria.attendance.model.LeaveGrantBasis;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaveGrantRuleUpsertRequest(
        @NotBlank String calendarCode,
        @NotBlank String leaveTypeCode,
        @NotNull LeaveGrantBasis grantBasis,
        @Min(0) int minTenureMonths,
        Integer maxTenureMonths,
        @Min(1) int grantDays,
        @Min(1) int validMonths,
        String note
) {
}
