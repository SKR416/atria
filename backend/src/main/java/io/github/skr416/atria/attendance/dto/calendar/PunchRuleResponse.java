package io.github.skr416.atria.attendance.dto.calendar;

import io.github.skr416.atria.attendance.model.PunchRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PunchRuleResponse {

    private Long id;
    private String calendarCode;
    private int lateGraceMinutes;
    private int earlyLeaveGraceMinutes;
    private int clockInRoundingMinutes;
    private int clockOutRoundingMinutes;
    private int dayCutoffHour;
    private int minPunchIntervalSeconds;
    private int maxRetroactiveDays;
    private int autoMonthlyCloseDay;
    private Instant updatedAt;

    public static PunchRuleResponse from(PunchRule rule) {
        return PunchRuleResponse.builder()
                .id(rule.getId())
                .calendarCode(rule.getCalendarCode())
                .lateGraceMinutes(rule.getLateGraceMinutes())
                .earlyLeaveGraceMinutes(rule.getEarlyLeaveGraceMinutes())
                .clockInRoundingMinutes(rule.getClockInRoundingMinutes())
                .clockOutRoundingMinutes(rule.getClockOutRoundingMinutes())
                .dayCutoffHour(rule.getDayCutoffHour())
                .minPunchIntervalSeconds(rule.getMinPunchIntervalSeconds())
                .maxRetroactiveDays(rule.getMaxRetroactiveDays())
                .autoMonthlyCloseDay(rule.getAutoMonthlyCloseDay())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
