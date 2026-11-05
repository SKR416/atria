package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.model.AttendanceStatus;
import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchRule;
import io.github.skr416.atria.attendance.repository.DailyAttendanceRecordRepository;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.attendance.repository.PunchRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calculation engine for evaluating raw punch events into effective timestamps,
 * net worked hours, lateness/early departure, and derived daily attendance records (ADR-0003 & ADR-0008).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceRecordCalculator {

    private final WorkScheduleResolver workScheduleResolver;
    private final PunchRuleRepository punchRuleRepository;
    private final PunchLogRepository punchLogRepository;
    private final DailyAttendanceRecordRepository dailyAttendanceRecordRepository;

    /**
     * Resolves the logical work date for a UTC punch timestamp based on timezone and daily cutoff hour.
     * Punches before {@code dayCutoffHour} (e.g. 04:00 AM) belong to the previous calendar day.
     */
    public LocalDate resolveLogicalWorkDate(Instant utcTimestamp, ZoneId timezone, int dayCutoffHour) {
        if (utcTimestamp == null) {
            utcTimestamp = Instant.now();
        }
        if (timezone == null) {
            timezone = ZoneId.of("Asia/Taipei");
        }
        ZonedDateTime zdt = utcTimestamp.atZone(timezone);
        if (zdt.getHour() < dayCutoffHour) {
            return zdt.toLocalDate().minusDays(1);
        }
        return zdt.toLocalDate();
    }

    /**
     * Fetches regional punch rules by calendar code, or returns sensible statutory defaults if unseeded.
     */
    public PunchRule resolvePunchRule(String calendarCode) {
        String code = (calendarCode != null && !calendarCode.isBlank()) ? calendarCode : "TW";
        return punchRuleRepository.findByCalendarCode(code).orElseGet(() -> {
            PunchRule defaultRule = new PunchRule();
            defaultRule.setCalendarCode(code);
            defaultRule.setLateGraceMinutes(5);
            defaultRule.setEarlyLeaveGraceMinutes(0);
            defaultRule.setClockInRoundingMinutes(1);
            defaultRule.setClockOutRoundingMinutes(1);
            defaultRule.setDayCutoffHour(4);
            defaultRule.setMinPunchIntervalSeconds(60);
            defaultRule.setMaxRetroactiveDays(14);
            defaultRule.setAutoMonthlyCloseDay(5);
            return defaultRule;
        });
    }

    /**
     * Truncates seconds/nanos to 0 and rounds UP (ceiling) to the nearest multiple of {@code roundingMinutes}.
     */
    public Instant applyClockInRounding(Instant rawTime, ZoneId timezone, int roundingMinutes) {
        if (rawTime == null) {
            return null;
        }
        Instant truncated = rawTime.truncatedTo(ChronoUnit.MINUTES);
        if (roundingMinutes <= 1) {
            return truncated;
        }
        if (timezone == null) {
            timezone = ZoneId.of("Asia/Taipei");
        }
        ZonedDateTime zdt = truncated.atZone(timezone);
        int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
        int remainder = minuteOfDay % roundingMinutes;
        if (remainder != 0) {
            zdt = zdt.plusMinutes(roundingMinutes - remainder);
        }
        return zdt.toInstant();
    }

    /**
     * Truncates seconds/nanos to 0 and rounds DOWN (floor) to the nearest multiple of {@code roundingMinutes}.
     */
    public Instant applyClockOutRounding(Instant rawTime, ZoneId timezone, int roundingMinutes) {
        if (rawTime == null) {
            return null;
        }
        Instant truncated = rawTime.truncatedTo(ChronoUnit.MINUTES);
        if (roundingMinutes <= 1) {
            return truncated;
        }
        if (timezone == null) {
            timezone = ZoneId.of("Asia/Taipei");
        }
        ZonedDateTime zdt = truncated.atZone(timezone);
        int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
        int remainder = minuteOfDay % roundingMinutes;
        if (remainder != 0) {
            zdt = zdt.minusMinutes(remainder);
        }
        return zdt.toInstant();
    }

    /**
     * Calculates net worked minutes between effective clock-in and clock-out,
     * deducting standard unpaid lunch break overlap (default 12:00–13:00, capped at 60 mins).
     */
    public int calculateNetWorkedMinutes(
            Instant effectiveIn,
            Instant effectiveOut,
            LocalDate workDate,
            ZoneId timezone,
            LocalTime breakStart,
            LocalTime breakEnd
    ) {
        if (effectiveIn == null || effectiveOut == null || !effectiveOut.isAfter(effectiveIn)) {
            return 0;
        }
        long totalMinutes = Duration.between(effectiveIn, effectiveOut).toMinutes();

        LocalTime bStart = breakStart != null ? breakStart : LocalTime.of(12, 0);
        LocalTime bEnd = breakEnd != null ? breakEnd : LocalTime.of(13, 0);

        if (bEnd.isAfter(bStart)) {
            Instant breakStartInstant = workDate.atTime(bStart).atZone(timezone).toInstant();
            Instant breakEndInstant = workDate.atTime(bEnd).atZone(timezone).toInstant();

            Instant overlapStart = effectiveIn.isAfter(breakStartInstant) ? effectiveIn : breakStartInstant;
            Instant overlapEnd = effectiveOut.isBefore(breakEndInstant) ? effectiveOut : breakEndInstant;

            if (overlapEnd.isAfter(overlapStart)) {
                long overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes();
                totalMinutes -= Math.min(60, overlapMinutes);
            }
        }

        return (int) Math.max(0, totalMinutes);
    }

    /**
     * Recomputes derived {@link DailyAttendanceRecord} for an employee on a given logical work date.
     * Evaluates effective clock times, worked duration, lateness/early departure, and status.
     *
     * @param userId employee ID
     * @param workDate logical work date
     * @param settledPass true when executed as part of the daily settlement job
     */
    @Transactional
    public DailyAttendanceRecord recomputeDailyRecord(Long userId, LocalDate workDate, boolean settledPass) {
        ResolvedWorkSchedule schedule = workScheduleResolver.resolve(userId, workDate);
        PunchRule rule = resolvePunchRule(schedule.calendarCode());
        ZoneId timezone = schedule.timezone() != null ? schedule.timezone() : ZoneId.of("Asia/Taipei");

        List<PunchLog> logs = punchLogRepository.findByUserIdAndWorkDateOrderByRawPunchTimeAsc(userId, workDate);
        DailyAttendanceRecord record = dailyAttendanceRecordRepository
                .findByUserIdAndWorkDate(userId, workDate)
                .orElseGet(DailyAttendanceRecord::new);

        record.setUserId(userId);
        record.setDepartmentId(schedule.departmentId());
        record.setCalendarCode(schedule.calendarCode());
        record.setWorkDate(workDate);
        record.setDayType(schedule.dayType());
        record.setScheduledStartTime(schedule.workStartTime());
        record.setScheduledEndTime(schedule.workEndTime());
        record.setPunchCount(logs.size());

        if (!logs.isEmpty()) {
            Instant firstRaw = logs.get(0).getRawPunchTime();
            Instant lastRaw = logs.size() >= 2 ? logs.get(logs.size() - 1).getRawPunchTime() : null;
            record.setFirstRawClockIn(firstRaw);
            record.setLastRawClockOut(lastRaw);

            Instant effectiveIn = applyClockInRounding(firstRaw, timezone, rule.getClockInRoundingMinutes());
            Instant effectiveOut = lastRaw != null
                    ? applyClockOutRounding(lastRaw, timezone, rule.getClockOutRoundingMinutes())
                    : null;
            record.setEffectiveClockIn(effectiveIn);
            record.setEffectiveClockOut(effectiveOut);

            int workedMinutes = calculateNetWorkedMinutes(
                    effectiveIn,
                    effectiveOut,
                    workDate,
                    timezone,
                    schedule.breakStartTime(),
                    schedule.breakEndTime()
            );
            record.setWorkedMinutes(workedMinutes);

            int lateMinutes = 0;
            int earlyLeaveMinutes = 0;

            if (schedule.isWorkingDay()) {
                if (schedule.workStartTime() != null) {
                    Instant scheduledStartInstant = workDate.atTime(schedule.workStartTime()).atZone(timezone).toInstant();
                    Instant graceStartInstant = scheduledStartInstant.plusSeconds(rule.getLateGraceMinutes() * 60L);
                    Instant firstRawTruncated = firstRaw.truncatedTo(ChronoUnit.MINUTES);
                    if (firstRawTruncated.isAfter(graceStartInstant)) {
                        lateMinutes = (int) Duration.between(scheduledStartInstant, effectiveIn).toMinutes();
                        if (lateMinutes < 0) {
                            lateMinutes = 0;
                        }
                    }
                }
                if (lastRaw != null && schedule.workEndTime() != null) {
                    Instant scheduledEndInstant = workDate.atTime(schedule.workEndTime()).atZone(timezone).toInstant();
                    Instant graceEndInstant = scheduledEndInstant.minusSeconds(rule.getEarlyLeaveGraceMinutes() * 60L);
                    if (effectiveOut.isBefore(graceEndInstant)) {
                        earlyLeaveMinutes = (int) Duration.between(effectiveOut, scheduledEndInstant).toMinutes();
                        if (earlyLeaveMinutes < 0) {
                            earlyLeaveMinutes = 0;
                        }
                    }
                }
            }
            record.setLateMinutes(lateMinutes);
            record.setEarlyLeaveMinutes(earlyLeaveMinutes);

            AttendanceStatus status;
            if (record.getLeaveMinutes() >= 480) {
                status = AttendanceStatus.ON_LEAVE;
            } else if (!schedule.isWorkingDay()) {
                status = AttendanceStatus.NORMAL;
            } else if (lastRaw == null) {
                if (settledPass) {
                    status = AttendanceStatus.MISSING_PUNCH;
                } else {
                    status = lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.NORMAL;
                }
            } else {
                if (lateMinutes > 0) {
                    status = AttendanceStatus.LATE;
                } else if (earlyLeaveMinutes > 0) {
                    status = AttendanceStatus.EARLY_LEAVE;
                } else {
                    status = AttendanceStatus.NORMAL;
                }
            }
            record.setStatus(status);
        } else {
            record.setFirstRawClockIn(null);
            record.setLastRawClockOut(null);
            record.setEffectiveClockIn(null);
            record.setEffectiveClockOut(null);
            record.setWorkedMinutes(0);
            record.setLateMinutes(0);
            record.setEarlyLeaveMinutes(0);

            AttendanceStatus status;
            if (record.getLeaveMinutes() > 0) {
                status = AttendanceStatus.ON_LEAVE;
            } else if (!schedule.isWorkingDay()) {
                status = AttendanceStatus.HOLIDAY;
            } else {
                status = AttendanceStatus.ABSENT;
            }
            record.setStatus(status);
        }

        if (settledPass) {
            record.setSettled(true);
        }

        return dailyAttendanceRecordRepository.save(record);
    }
}
