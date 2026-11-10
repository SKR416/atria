package io.github.skr416.atria.attendance.dto.overtime;

import io.github.skr416.atria.attendance.model.OvertimeCapAction;
import io.github.skr416.atria.attendance.model.OvertimeCapRule;

import java.util.Locale;
import java.util.Map;

public record OvertimeCapRuleResponse(
        Long id,
        String calendarCode,
        String ruleCode,
        Map<String, String> name,
        String localizedName,
        int periodMonths,
        int maxMinutes,
        OvertimeCapAction enforcementAction,
        boolean enabled,
        String description
) {
    public static OvertimeCapRuleResponse from(OvertimeCapRule r, Locale locale) {
        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
        Map<String, String> map = r.getName() != null ? r.getName() : Map.of();
        String localized = map.getOrDefault(langTag,
                map.getOrDefault("zh-TW",
                        map.getOrDefault("en-US", r.getRuleCode())));
        return new OvertimeCapRuleResponse(
                r.getId(),
                r.getCalendarCode(),
                r.getRuleCode(),
                map,
                localized,
                r.getPeriodMonths(),
                r.getMaxMinutes(),
                r.getEnforcementAction(),
                r.isEnabled(),
                r.getDescription()
        );
    }
}
