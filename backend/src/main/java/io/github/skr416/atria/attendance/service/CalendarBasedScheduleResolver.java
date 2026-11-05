package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.calendar.StatutoryHolidayTemplateGenerator;
import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import io.github.skr416.atria.attendance.repository.WorkCalendarRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Calendar-driven implementation of {@link WorkScheduleResolver} that resolves shift hours,
 * timezone, and day types via department settings and regional work calendars (ADR-0003 & ADR-0008).
 */
@Service
public class CalendarBasedScheduleResolver implements WorkScheduleResolver {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkCalendarRepository workCalendarRepository;
    private final List<StatutoryHolidayTemplateGenerator> generators;

    public CalendarBasedScheduleResolver(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            WorkCalendarRepository workCalendarRepository,
            @Autowired(required = false) List<StatutoryHolidayTemplateGenerator> generators
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.workCalendarRepository = workCalendarRepository;
        this.generators = generators != null ? generators : List.of();
    }

    @Override
    public ResolvedWorkSchedule resolve(Long userId, LocalDate workDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String calendarCode = "TW";
        String timezone = "Asia/Taipei";
        Long departmentId = user.getDepartmentId();

        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId).orElse(null);
            if (department != null) {
                if (department.getCalendarCode() != null && !department.getCalendarCode().isBlank()) {
                    calendarCode = department.getCalendarCode();
                }
                if (department.getTimezone() != null && !department.getTimezone().isBlank()) {
                    timezone = department.getTimezone();
                }
            }
        }

        ResolvedWorkSchedule base = resolveForCalendar(calendarCode, timezone, workDate);
        return new ResolvedWorkSchedule(
                userId,
                departmentId,
                base.calendarCode(),
                base.timezone(),
                base.workDate(),
                base.dayType(),
                base.workStartTime(),
                base.workEndTime(),
                base.breakStartTime(),
                base.breakEndTime(),
                base.holidayName()
        );
    }

    @Override
    public ResolvedWorkSchedule resolveForCalendar(String calendarCode, String timezone, LocalDate workDate) {
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";

        ZoneId zoneId;
        if (timezone == null || timezone.isBlank()) {
            zoneId = ZoneId.of("Asia/Taipei");
        } else {
            try {
                zoneId = ZoneId.of(timezone.trim());
            } catch (Exception ex) {
                zoneId = ZoneId.of("Asia/Taipei");
            }
        }

        Optional<WorkCalendarDay> opt = workCalendarRepository.findByCalendarCodeAndWorkDate(code, workDate);
        if (opt.isPresent()) {
            WorkCalendarDay day = opt.get();
            return new ResolvedWorkSchedule(
                    null,
                    null,
                    code,
                    zoneId,
                    workDate,
                    day.getDayType(),
                    day.getWorkStartTime(),
                    day.getWorkEndTime(),
                    day.getBreakStartTime(),
                    day.getBreakEndTime(),
                    day.getHolidayName()
            );
        }

        StatutoryHolidayTemplateGenerator matched = generators.stream()
                .filter(g -> g.getCalendarCode() != null && g.getCalendarCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);

        if (matched != null) {
            WorkCalendarDay generated = matched.resolveDay(workDate);
            return new ResolvedWorkSchedule(
                    null,
                    null,
                    code,
                    zoneId,
                    workDate,
                    generated.getDayType(),
                    generated.getWorkStartTime(),
                    generated.getWorkEndTime(),
                    generated.getBreakStartTime(),
                    generated.getBreakEndTime(),
                    generated.getHolidayName()
            );
        }

        DayOfWeek dow = workDate.getDayOfWeek();
        CalendarDayType fallbackType;
        if (dow == DayOfWeek.SATURDAY) {
            fallbackType = CalendarDayType.REST_DAY;
        } else if (dow == DayOfWeek.SUNDAY) {
            fallbackType = CalendarDayType.STATUTORY_REST_DAY;
        } else {
            fallbackType = CalendarDayType.WORKDAY;
        }

        return new ResolvedWorkSchedule(
                null,
                null,
                code,
                zoneId,
                workDate,
                fallbackType,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                null
        );
    }
}
