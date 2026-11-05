package io.github.skr416.atria.attendance.calendar;

import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statutory holiday calendar generator for Taiwan ({@code "TW"}), aligned with the Taiwan Labor Standards Act
 * (勞動基準法) and the Enforcement Regulations on Commemoration Days and Holidays (紀念日及節日實施辦法)
 * (ADR-0008 Decision 2).
 */
@Component
public class TaiwanHolidayGenerator implements StatutoryHolidayTemplateGenerator {

    private static final String CALENDAR_CODE = "TW";
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_BREAK_END = LocalTime.of(13, 0);

    private record LunarHolidays(LocalDate lny1, LocalDate dragonBoat, LocalDate midAutumn) {}

    private static final Map<Integer, LunarHolidays> LUNAR_TABLE = Map.ofEntries(
            Map.entry(2020, new LunarHolidays(LocalDate.of(2020, 1, 25), LocalDate.of(2020, 6, 25), LocalDate.of(2020, 10, 1))),
            Map.entry(2021, new LunarHolidays(LocalDate.of(2021, 2, 12), LocalDate.of(2021, 6, 14), LocalDate.of(2021, 9, 21))),
            Map.entry(2022, new LunarHolidays(LocalDate.of(2022, 2, 1), LocalDate.of(2022, 6, 3), LocalDate.of(2022, 9, 10))),
            Map.entry(2023, new LunarHolidays(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 6, 22), LocalDate.of(2023, 9, 29))),
            Map.entry(2024, new LunarHolidays(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 6, 10), LocalDate.of(2024, 9, 17))),
            Map.entry(2025, new LunarHolidays(LocalDate.of(2025, 1, 29), LocalDate.of(2025, 5, 31), LocalDate.of(2025, 10, 6))),
            Map.entry(2026, new LunarHolidays(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 6, 19), LocalDate.of(2026, 9, 25))),
            Map.entry(2027, new LunarHolidays(LocalDate.of(2027, 2, 6), LocalDate.of(2027, 6, 9), LocalDate.of(2027, 9, 15))),
            Map.entry(2028, new LunarHolidays(LocalDate.of(2028, 1, 26), LocalDate.of(2028, 5, 28), LocalDate.of(2028, 10, 3))),
            Map.entry(2029, new LunarHolidays(LocalDate.of(2029, 2, 13), LocalDate.of(2029, 6, 16), LocalDate.of(2029, 9, 22))),
            Map.entry(2030, new LunarHolidays(LocalDate.of(2030, 2, 3), LocalDate.of(2030, 6, 5), LocalDate.of(2030, 9, 12))),
            Map.entry(2031, new LunarHolidays(LocalDate.of(2031, 1, 23), LocalDate.of(2031, 6, 24), LocalDate.of(2031, 10, 1))),
            Map.entry(2032, new LunarHolidays(LocalDate.of(2032, 2, 11), LocalDate.of(2032, 6, 12), LocalDate.of(2032, 9, 19))),
            Map.entry(2033, new LunarHolidays(LocalDate.of(2033, 1, 31), LocalDate.of(2033, 6, 1), LocalDate.of(2033, 10, 8))),
            Map.entry(2034, new LunarHolidays(LocalDate.of(2034, 2, 19), LocalDate.of(2034, 6, 20), LocalDate.of(2034, 9, 27))),
            Map.entry(2035, new LunarHolidays(LocalDate.of(2035, 2, 8), LocalDate.of(2035, 6, 10), LocalDate.of(2035, 9, 16)))
    );

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

        // 1. Lunar New Year (Eve + Days 1-3)
        LunarHolidays lunar = resolveLunarHolidays(year);
        applyLunarNewYear(map, year, lunar.lny1());

        // 2. Solar Statutory Holidays
        // 01-01: 開國紀念日
        applySingleDayHoliday(map, year, LocalDate.of(year, 1, 1),
                StatutoryHolidayTemplateGenerator.i18n("開國紀念日", "New Year's Day", "元日"));
        // 02-28: 和平紀念日
        applySingleDayHoliday(map, year, LocalDate.of(year, 2, 28),
                StatutoryHolidayTemplateGenerator.i18n("和平紀念日", "Peace Memorial Day", "平和記念日"));

        // Children's Day (04-04) & Qingming (Tomb Sweeping Day)
        applyChildrenDayAndQingming(map, year);

        // 05-01: 勞動節
        applySingleDayHoliday(map, year, LocalDate.of(year, 5, 1),
                StatutoryHolidayTemplateGenerator.i18n("勞動節", "Labor Day", "メーデー"));
        // 10-10: 國慶日
        applySingleDayHoliday(map, year, LocalDate.of(year, 10, 10),
                StatutoryHolidayTemplateGenerator.i18n("國慶日", "National Day", "国慶節"));

        // 3. Lunar Single-Day Holidays
        // Dragon Boat Festival (端午節)
        applySingleDayHoliday(map, year, lunar.dragonBoat(),
                StatutoryHolidayTemplateGenerator.i18n("端午節", "Dragon Boat Festival", "端午節"));
        // Mid-Autumn Festival (中秋節)
        applySingleDayHoliday(map, year, lunar.midAutumn(),
                StatutoryHolidayTemplateGenerator.i18n("中秋節", "Mid-Autumn Festival", "中秋節"));

        List<WorkCalendarDay> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing(WorkCalendarDay::getWorkDate));
        return result;
    }

    private void applyLunarNewYear(Map<LocalDate, WorkCalendarDay> map, int year, LocalDate lny1) {
        Map<String, String> eveName = StatutoryHolidayTemplateGenerator.i18n("除夕", "Lunar New Year's Eve", "旧暦大晦日");
        Map<String, String> lnyName = StatutoryHolidayTemplateGenerator.i18n("春節初一至初三", "Lunar New Year", "春節");

        LocalDate eve = lny1.minusDays(1);
        LocalDate day1 = lny1;
        LocalDate day2 = lny1.plusDays(1);
        LocalDate day3 = lny1.plusDays(2);

        LocalDate[] block = {eve, day1, day2, day3};
        int weekendCount = 0;

        for (int i = 0; i < block.length; i++) {
            LocalDate d = block[i];
            if (d.getYear() != year) {
                continue;
            }
            WorkCalendarDay day = map.get(d);
            if (day == null) {
                continue;
            }
            Map<String, String> name = (i == 0) ? eveName : lnyName;
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                day.setHolidayName(name);
                weekendCount++;
            } else {
                day.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                day.setHolidayName(name);
            }
        }

        // Extend to next available WORKDAY after Day 3
        Map<String, String> observedName = withObservedSuffix(lnyName);
        LocalDate candidate = day3.plusDays(1);
        while (weekendCount > 0 && candidate.getYear() == year) {
            WorkCalendarDay candidateDay = map.get(candidate);
            if (candidateDay != null && candidateDay.getDayType() == CalendarDayType.WORKDAY) {
                candidateDay.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                candidateDay.setHolidayName(observedName);
                weekendCount--;
            }
            candidate = candidate.plusDays(1);
        }
    }

    private void applyChildrenDayAndQingming(Map<LocalDate, WorkCalendarDay> map, int year) {
        int qingmingDay = (int) ((year - 2000) * 0.2422 + 4.81) - (year - 2000) / 4;
        Map<String, String> childrenName = StatutoryHolidayTemplateGenerator.i18n("兒童節", "Children's Day", "児童の日");
        Map<String, String> qingmingName = StatutoryHolidayTemplateGenerator.i18n("清明節", "Tomb Sweeping Day", "清明節");

        LocalDate childrenDate;
        LocalDate qingmingDate;

        if (qingmingDay == 4) {
            LocalDate apr4 = LocalDate.of(year, 4, 4);
            if (apr4.getDayOfWeek() == DayOfWeek.THURSDAY) {
                childrenDate = apr4;
                qingmingDate = LocalDate.of(year, 4, 5);
            } else {
                childrenDate = LocalDate.of(year, 4, 3);
                qingmingDate = apr4;
            }
        } else {
            childrenDate = LocalDate.of(year, 4, 4);
            qingmingDate = LocalDate.of(year, 4, qingmingDay);
        }

        applySingleDayHoliday(map, year, childrenDate, childrenName);
        applySingleDayHoliday(map, year, qingmingDate, qingmingName);
    }

    private void applySingleDayHoliday(Map<LocalDate, WorkCalendarDay> map, int year, LocalDate date, Map<String, String> name) {
        if (date.getYear() != year) {
            return;
        }
        WorkCalendarDay day = map.get(date);
        if (day == null) {
            return;
        }

        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            day.setHolidayName(name);
            LocalDate friday = date.minusDays(1);
            if (friday.getYear() == year) {
                WorkCalendarDay fridayDay = map.get(friday);
                if (fridayDay != null && fridayDay.getDayType() == CalendarDayType.WORKDAY) {
                    fridayDay.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                    fridayDay.setHolidayName(withObservedSuffix(name));
                }
            }
        } else if (dow == DayOfWeek.SUNDAY) {
            day.setHolidayName(name);
            LocalDate monday = date.plusDays(1);
            if (monday.getYear() == year) {
                WorkCalendarDay mondayDay = map.get(monday);
                if (mondayDay != null && mondayDay.getDayType() == CalendarDayType.WORKDAY) {
                    mondayDay.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
                    mondayDay.setHolidayName(withObservedSuffix(name));
                }
            }
        } else {
            day.setDayType(CalendarDayType.NATIONAL_HOLIDAY);
            day.setHolidayName(name);
        }
    }

    private static Map<String, String> withObservedSuffix(Map<String, String> name) {
        return StatutoryHolidayTemplateGenerator.i18n(
                name.getOrDefault("zh-TW", "") + "（補假）",
                name.getOrDefault("en-US", "") + " (Observed)",
                name.getOrDefault("ja-JP", "") + "（振替休日）"
        );
    }

    private LunarHolidays resolveLunarHolidays(int year) {
        LunarHolidays holidays = LUNAR_TABLE.get(year);
        if (holidays != null) {
            return holidays;
        }
        return new LunarHolidays(
                LocalDate.of(year, 2, 1),
                LocalDate.of(year, 6, 10),
                LocalDate.of(year, 9, 20)
        );
    }
}
