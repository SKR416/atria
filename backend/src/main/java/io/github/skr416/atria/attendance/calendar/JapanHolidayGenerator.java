package io.github.skr416.atria.attendance.calendar;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statutory holiday calendar generator for Japan ({@code "JP"}), aligned with the Cabinet Office
 * Act on National Holidays (国民の祝日に関する法律) (ADR-0008 Decision 2).
 */
@Component
public class JapanHolidayGenerator implements StatutoryHolidayTemplateGenerator {

    private static final String CALENDAR_CODE = "JP";
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_BREAK_END = LocalTime.of(13, 0);

    @Override
    public String getCalendarCode() {
        return CALENDAR_CODE;
    }

    @Override
    public List<WorkCalendarDay> generateYear(int year) {
        Map<LocalDate, WorkCalendarDay> map = new LinkedHashMap<>();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            WorkCalendarDay day = new WorkCalendarDay();
            day.setCalendarCode(CALENDAR_CODE);
            day.setWorkDate(date);
            day.setWorkStartTime(DEFAULT_WORK_START);
            day.setWorkEndTime(DEFAULT_WORK_END);
            day.setBreakStartTime(DEFAULT_BREAK_START);
            day.setBreakEndTime(DEFAULT_BREAK_END);

            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY) {
                day.setDayType(CalendarDayType.REST_DAY);
            } else if (dow == DayOfWeek.SUNDAY) {
                day.setDayType(CalendarDayType.STATUTORY_REST_DAY);
            } else {
                day.setDayType(CalendarDayType.WORKDAY);
            }
            map.put(date, day);
        }

        // 1. Primary Statutory Holidays
        Map<LocalDate, Map<String, String>> primaryHolidays = collectPrimaryHolidays(year);
        for (Map.Entry<LocalDate, Map<String, String>> entry : primaryHolidays.entrySet()) {
            WorkCalendarDay day = map.get(entry.getKey());
            if (day != null) {
                day.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                day.setHolidayName(entry.getValue());
            }
        }

        // 2. Citizens' Holiday (国民の休日):
        // Any working day (WORKDAY) sandwiched immediately between two primary NATIONAL_HOLIDAY dates
        Map<String, String> citizensHolidayName = StatutoryHolidayTemplateGenerator.i18n(
                "國民之休日", "Citizens' Holiday", "国民の休日"
        );
        for (LocalDate date = LocalDate.of(year, 1, 2); date.isBefore(LocalDate.of(year, 12, 31)); date = date.plusDays(1)) {
            WorkCalendarDay currentDay = map.get(date);
            if (currentDay != null && currentDay.getDayType() == CalendarDayType.WORKDAY) {
                LocalDate prev = date.minusDays(1);
                LocalDate next = date.plusDays(1);
                if (primaryHolidays.containsKey(prev) && primaryHolidays.containsKey(next)) {
                    currentDay.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                    currentDay.setHolidayName(citizensHolidayName);
                }
            }
        }

        // 3. Substitute Holiday (振替休日):
        // When a primary NATIONAL_HOLIDAY falls on Sunday, the next earliest day that is NOT already
        // a NATIONAL_HOLIDAY becomes NATIONAL_HOLIDAY.
        Map<String, String> substituteHolidayName = StatutoryHolidayTemplateGenerator.i18n(
                "振替休日", "Substitute Holiday", "振替休日"
        );
        for (Map.Entry<LocalDate, Map<String, String>> entry : primaryHolidays.entrySet()) {
            LocalDate holidayDate = entry.getKey();
            if (holidayDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate candidate = holidayDate.plusDays(1);
                while (candidate.getYear() == year) {
                    WorkCalendarDay candidateDay = map.get(candidate);
                    if (candidateDay != null && candidateDay.getDayType() != CalendarDayType.NATIONAL_HOLIDAY) {
                        candidateDay.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                        candidateDay.setHolidayName(substituteHolidayName);
                        break;
                    }
                    candidate = candidate.plusDays(1);
                }
            }
        }

        List<WorkCalendarDay> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing(WorkCalendarDay::getWorkDate));
        return result;
    }

    private Map<LocalDate, Map<String, String>> collectPrimaryHolidays(int year) {
        Map<LocalDate, Map<String, String>> holidays = new LinkedHashMap<>();

        // Fixed dates
        holidays.put(LocalDate.of(year, 1, 1),
                StatutoryHolidayTemplateGenerator.i18n("元旦", "New Year's Day", "元日"));
        holidays.put(LocalDate.of(year, 2, 11),
                StatutoryHolidayTemplateGenerator.i18n("建國紀念之日", "National Foundation Day", "建国記念の日"));
        if (year >= 2020) {
            holidays.put(LocalDate.of(year, 2, 23),
                    StatutoryHolidayTemplateGenerator.i18n("天皇誕生日", "Emperor's Birthday", "天皇誕生日"));
        }
        holidays.put(LocalDate.of(year, 4, 29),
                StatutoryHolidayTemplateGenerator.i18n("昭和之日", "Showa Day", "昭和の日"));
        holidays.put(LocalDate.of(year, 5, 3),
                StatutoryHolidayTemplateGenerator.i18n("憲法紀念日", "Constitution Memorial Day", "憲法記念日"));
        holidays.put(LocalDate.of(year, 5, 4),
                StatutoryHolidayTemplateGenerator.i18n("綠之日", "Greenery Day", "みどりの日"));
        holidays.put(LocalDate.of(year, 5, 5),
                StatutoryHolidayTemplateGenerator.i18n("兒童之日", "Children's Day", "こどもの日"));
        holidays.put(LocalDate.of(year, 8, 11),
                StatutoryHolidayTemplateGenerator.i18n("山之日", "Mountain Day", "山の日"));
        holidays.put(LocalDate.of(year, 11, 3),
                StatutoryHolidayTemplateGenerator.i18n("文化之日", "Culture Day", "文化の日"));
        holidays.put(LocalDate.of(year, 11, 23),
                StatutoryHolidayTemplateGenerator.i18n("勤勞感謝之日", "Labor Thanksgiving Day", "勤労感謝の日"));

        // Happy Monday holidays (using TemporalAdjusters.dayOfWeekInMonth)
        // Coming of Age Day: 2nd Monday of January
        holidays.put(LocalDate.of(year, 1, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)),
                StatutoryHolidayTemplateGenerator.i18n("成人之日", "Coming of Age Day", "成人の日"));
        // Marine Day: 3rd Monday of July
        holidays.put(LocalDate.of(year, 7, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)),
                StatutoryHolidayTemplateGenerator.i18n("海之日", "Marine Day", "海の日"));
        // Respect for the Aged Day: 3rd Monday of September
        holidays.put(LocalDate.of(year, 9, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)),
                StatutoryHolidayTemplateGenerator.i18n("敬老之日", "Respect for the Aged Day", "敬老の日"));
        // Sports Day: 2nd Monday of October
        holidays.put(LocalDate.of(year, 10, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)),
                StatutoryHolidayTemplateGenerator.i18n("運動之日", "Sports Day", "スポーツの日"));

        // Astronomical Equinox holidays (Cabinet Office / NAOJ formula for 1980–2099)
        int vernalDay = (int) (20.8431 + 0.242194 * (year - 1980)) - (year - 1980) / 4;
        holidays.put(LocalDate.of(year, 3, vernalDay),
                StatutoryHolidayTemplateGenerator.i18n("春分之日", "Vernal Equinox Day", "春分の日"));

        int autumnalDay = (int) (23.2488 + 0.242194 * (year - 1980)) - (year - 1980) / 4;
        holidays.put(LocalDate.of(year, 9, autumnalDay),
                StatutoryHolidayTemplateGenerator.i18n("秋分之日", "Autumnal Equinox Day", "秋分の日"));

        return holidays;
    }
}
