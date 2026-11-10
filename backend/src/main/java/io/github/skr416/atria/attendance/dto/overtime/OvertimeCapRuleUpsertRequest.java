package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.OvertimeCapAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record OvertimeCapRuleUpsertRequest(
        @NotBlank String calendarCode,
        @NotBlank String ruleCode,
        @NotEmpty Map<String, String> name,
        @Min(1) int periodMonths,
        @Min(1) int maxMinutes,
        @NotNull OvertimeCapAction enforcementAction,
        boolean enabled,
        String description
) {
}
