package io.github.skr416.atria.attendance.calendar;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Strategy contract for generating regional statutory work calendars (e.g. {@code TW}, {@code JP})
 * for any given year (ADR-0008 Decision 2).
 */
public interface StatutoryHolidayTemplateGenerator {

    /**
     * Region calendar code handled by this generator (e.g. {@code "TW"} or {@code "JP"}), uppercase.
     */
    String getCalendarCode();

    /**
     * Generates all 365/366 days of the given year as transient {@link WorkCalendarDay} instances
     * populated with {@link CalendarDayType}, standard shift hours (09:00-18:00, break 12:00-13:00),
     * and trilingual {@code holidayName} map ({@code zh-TW}, {@code en-US}, {@code ja-JP}) for holidays.
     */
    List<WorkCalendarDay> generateYear(int year);

    /**
     * Resolves a single date's default template entry when the year has not yet been persisted in DB.
     */
    default WorkCalendarDay resolveDay(LocalDate date) {
        return generateYear(date.getYear()).stream()
                .filter(d -> date.equals(d.getWorkDate()))
                .findFirst()
                .orElseGet(() -> {
                    WorkCalendarDay fallback = new WorkCalendarDay();
                    fallback.setCalendarCode(getCalendarCode());
                    fallback.setWorkDate(date);
                    int dow = date.getDayOfWeek().getValue();
                    if (dow == 6) {
                        fallback.setDayType(CalendarDayType.REST_DAY);
                    } else if (dow == 7) {
                        fallback.setDayType(CalendarDayType.STATUTORY_REST_DAY);
                    } else {
                        fallback.setDayType(CalendarDayType.WORKDAY);
                    }
                    fallback.setWorkStartTime(LocalTime.of(9, 0));
                    fallback.setWorkEndTime(LocalTime.of(18, 0));
                    fallback.setBreakStartTime(LocalTime.of(12, 0));
                    fallback.setBreakEndTime(LocalTime.of(13, 0));
                    return fallback;
                });
    }

    /**
     * Helper to construct a trilingual name map.
     */
    static Map<String, String> i18n(String zhTw, String enUs, String jaJp) {
        return Map.of(
                "zh-TW", zhTw,
                "en-US", enUs,
                "ja-JP", jaJp
        );
    }
}
