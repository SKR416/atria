package io.github.skr416.atria.attendance.dto.leave;

import io.github.skr416.atria.attendance.model.LeaveType;

import java.util.Locale;
import java.util.Map;

public record LeaveTypeResponse(
        Long id,
        String code,
        Map<String, String> name,
        String localizedName,
        int minUnitMinutes,
        boolean paid,
        boolean balanceTracked,
        boolean attachmentRequired,
        boolean enabled,
        int sortOrder,
        String description
) {
    public static LeaveTypeResponse from(LeaveType entity, Locale locale) {
        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
        Map<String, String> map = entity.getName() != null ? entity.getName() : Map.of();
        String localized = map.getOrDefault(langTag,
                map.getOrDefault("zh-TW",
                        map.getOrDefault("en-US", entity.getCode())));
        return new LeaveTypeResponse(
                entity.getId(),
                entity.getCode(),
                map,
                localized,
                entity.getMinUnitMinutes(),
                entity.isPaid(),
                entity.isBalanceTracked(),
                entity.isAttachmentRequired(),
                entity.isEnabled(),
                entity.getSortOrder(),
                entity.getDescription()
        );
    }
}
