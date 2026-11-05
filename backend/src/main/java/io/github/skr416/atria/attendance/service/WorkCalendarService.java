package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.calendar.StatutoryHolidayTemplateGenerator;
import io.github.skr416.atria.attendance.dto.calendar.GenerateYearCalendarRequest;
import io.github.skr416.atria.attendance.dto.calendar.PunchRuleResponse;
import io.github.skr416.atria.attendance.dto.calendar.PunchRuleUpdateRequest;
import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayResponse;
import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayUpdateRequest;
import io.github.skr416.atria.attendance.model.PunchRule;
import io.github.skr416.atria.attendance.model.WorkCalendarDay;
import io.github.skr416.atria.attendance.repository.PunchRuleRepository;
import io.github.skr416.atria.attendance.repository.WorkCalendarRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service managing multi-region work calendars, statutory calendar auto-initialization,
 * and punch rounding/grace rules (ADR-0003 & ADR-0008 Decision 2).
 */
@Service
public class WorkCalendarService {

    private final WorkCalendarRepository workCalendarRepository;
    private final PunchRuleRepository punchRuleRepository;
    private final List<StatutoryHolidayTemplateGenerator> generators;

    public WorkCalendarService(
            WorkCalendarRepository workCalendarRepository,
            PunchRuleRepository punchRuleRepository,
            @Autowired(required = false) List<StatutoryHolidayTemplateGenerator> generators
    ) {
        this.workCalendarRepository = workCalendarRepository;
        this.punchRuleRepository = punchRuleRepository;
        this.generators = generators != null ? generators : List.of();
    }

    /**
     * Retrieves calendar days within the specified date range.
     * Auto-initializes and persists the whole year's template days if not yet present in DB.
     */
    @Transactional
    public List<WorkCalendarDayResponse> getCalendarDays(String calendarCode, LocalDate startDate, LocalDate endDate) {
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";

        if (startDate != null && endDate != null) {
            int startYear = Math.min(startDate.getYear(), endDate.getYear());
            int endYear = Math.max(startDate.getYear(), endDate.getYear());
            for (int year = startYear; year <= endYear; year++) {
                LocalDate yearStart = LocalDate.of(year, 1, 1);
                LocalDate yearEnd = LocalDate.of(year, 12, 31);
                List<WorkCalendarDay> existingInYear = workCalendarRepository
                        .findByCalendarCodeAndWorkDateBetweenOrderByWorkDateAsc(code, yearStart, yearEnd);
                if (existingInYear.isEmpty()) {
                    StatutoryHolidayTemplateGenerator generator = findGenerator(code);
                    if (generator != null) {
                        List<WorkCalendarDay> generated = generator.generateYear(year);
                        if (generated != null && !generated.isEmpty()) {
                            for (WorkCalendarDay day : generated) {
                                day.setCalendarCode(code);
                            }
                            workCalendarRepository.saveAll(generated);
                        }
                    }
                }
            }
        }

        List<WorkCalendarDay> days = workCalendarRepository
                .findByCalendarCodeAndWorkDateBetweenOrderByWorkDateAsc(code, startDate, endDate);
        Locale locale = LocaleContextHolder.getLocale();
        return days.stream()
                .map(day -> WorkCalendarDayResponse.from(day, locale))
                .toList();
    }

    /**
     * Generates all days of a year using the regional statutory holiday template generator.
     */
    @Transactional
    @AuditLog(action = "ATT_CALENDAR_GENERATE_YEAR", targetType = "WORK_CALENDAR")
    public List<WorkCalendarDayResponse> generateYear(GenerateYearCalendarRequest req) {
        if (req == null || req.calendarCode() == null || req.calendarCode().isBlank()) {
            throw new BusinessException(ErrorCode.ATT_UNSUPPORTED_CALENDAR_CODE);
        }
        String code = req.calendarCode().trim().toUpperCase();
        StatutoryHolidayTemplateGenerator generator = findGenerator(code);
        if (generator == null) {
            throw new BusinessException(ErrorCode.ATT_UNSUPPORTED_CALENDAR_CODE);
        }

        int year = req.year();
        List<WorkCalendarDay> generatedDays = generator.generateYear(year);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<WorkCalendarDay> existingDays = workCalendarRepository
                .findByCalendarCodeAndWorkDateBetweenOrderByWorkDateAsc(code, yearStart, yearEnd);
        Map<LocalDate, WorkCalendarDay> existingMap = existingDays.stream()
                .collect(Collectors.toMap(WorkCalendarDay::getWorkDate, d -> d));

        List<WorkCalendarDay> toSave = new ArrayList<>();
        for (WorkCalendarDay gen : generatedDays) {
            WorkCalendarDay existing = existingMap.get(gen.getWorkDate());
            if (existing != null) {
                if (req.overwriteExisting()) {
                    existing.setDayType(gen.getDayType());
                    existing.setWorkStartTime(gen.getWorkStartTime() != null ? gen.getWorkStartTime() : LocalTime.of(9, 0));
                    existing.setWorkEndTime(gen.getWorkEndTime() != null ? gen.getWorkEndTime() : LocalTime.of(18, 0));
                    existing.setBreakStartTime(gen.getBreakStartTime() != null ? gen.getBreakStartTime() : LocalTime.of(12, 0));
                    existing.setBreakEndTime(gen.getBreakEndTime() != null ? gen.getBreakEndTime() : LocalTime.of(13, 0));
                    existing.setHolidayName(gen.getHolidayName());
                    existing.setNote(gen.getNote());
                    toSave.add(existing);
                }
            } else {
                gen.setCalendarCode(code);
                toSave.add(gen);
            }
        }

        if (!toSave.isEmpty()) {
            workCalendarRepository.saveAll(toSave);
        }

        List<WorkCalendarDay> allDays = workCalendarRepository
                .findByCalendarCodeAndWorkDateBetweenOrderByWorkDateAsc(code, yearStart, yearEnd);
        Locale locale = LocaleContextHolder.getLocale();
        return allDays.stream()
                .map(d -> WorkCalendarDayResponse.from(d, locale))
                .toList();
    }

    /**
     * Upserts a single date in the work calendar.
     */
    @Transactional
    @AuditLog(action = "ATT_CALENDAR_UPSERT_DAY", targetType = "WORK_CALENDAR")
    public WorkCalendarDayResponse upsertDay(String calendarCode, LocalDate workDate, WorkCalendarDayUpdateRequest req) {
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";

        WorkCalendarDay day = workCalendarRepository.findByCalendarCodeAndWorkDate(code, workDate)
                .orElseGet(() -> {
                    WorkCalendarDay d = new WorkCalendarDay();
                    d.setCalendarCode(code);
                    d.setWorkDate(workDate);
                    return d;
                });

        day.setDayType(req.dayType());
        day.setWorkStartTime(req.workStartTime() != null ? req.workStartTime() : LocalTime.of(9, 0));
        day.setWorkEndTime(req.workEndTime() != null ? req.workEndTime() : LocalTime.of(18, 0));
        day.setBreakStartTime(req.breakStartTime() != null ? req.breakStartTime() : LocalTime.of(12, 0));
        day.setBreakEndTime(req.breakEndTime() != null ? req.breakEndTime() : LocalTime.of(13, 0));
        day.setHolidayName(req.holidayName());
        day.setNote(req.note());

        WorkCalendarDay saved = workCalendarRepository.save(day);
        return WorkCalendarDayResponse.from(saved, LocaleContextHolder.getLocale());
    }

    /**
     * Lists all regional punch rules.
     */
    public List<PunchRuleResponse> getAllPunchRules() {
        return punchRuleRepository.findAllByOrderByCalendarCodeAsc().stream()
                .map(PunchRuleResponse::from)
                .toList();
    }

    /**
     * Retrieves punch rule by calendar code.
     */
    public PunchRuleResponse getPunchRule(String calendarCode) {
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";
        PunchRule rule = punchRuleRepository.findByCalendarCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_PUNCH_RULE_NOT_FOUND));
        return PunchRuleResponse.from(rule);
    }

    /**
     * Updates regional punch and cutoff rule.
     */
    @Transactional
    @AuditLog(action = "ATT_PUNCH_RULE_UPDATE", targetType = "PUNCH_RULE")
    public PunchRuleResponse updatePunchRule(String calendarCode, PunchRuleUpdateRequest req) {
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";
        PunchRule rule = punchRuleRepository.findByCalendarCode(code)
                .orElseGet(() -> {
                    PunchRule r = new PunchRule();
                    r.setCalendarCode(code);
                    return r;
                });

        rule.setLateGraceMinutes(req.lateGraceMinutes());
        rule.setEarlyLeaveGraceMinutes(req.earlyLeaveGraceMinutes());
        rule.setClockInRoundingMinutes(req.clockInRoundingMinutes());
        rule.setClockOutRoundingMinutes(req.clockOutRoundingMinutes());
        rule.setDayCutoffHour(req.dayCutoffHour());
        rule.setMinPunchIntervalSeconds(req.minPunchIntervalSeconds());
        rule.setMaxRetroactiveDays(req.maxRetroactiveDays());
        rule.setAutoMonthlyCloseDay(req.autoMonthlyCloseDay());

        PunchRule saved = punchRuleRepository.save(rule);
        return PunchRuleResponse.from(saved);
    }

    private StatutoryHolidayTemplateGenerator findGenerator(String calendarCode) {
        if (calendarCode == null) {
            return null;
        }
        return generators.stream()
                .filter(g -> g.getCalendarCode() != null && g.getCalendarCode().equalsIgnoreCase(calendarCode))
                .findFirst()
                .orElse(null);
    }
}
