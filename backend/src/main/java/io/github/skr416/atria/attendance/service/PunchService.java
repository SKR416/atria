package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.calendar.WorkCalendarDayResponse;
import io.github.skr416.atria.attendance.dto.punch.BotPunchRequest;
import io.github.skr416.atria.attendance.dto.punch.DailyAttendanceRecordResponse;
import io.github.skr416.atria.attendance.dto.punch.MonthlyOverviewResponse;
import io.github.skr416.atria.attendance.dto.punch.PunchLogResponse;
import io.github.skr416.atria.attendance.dto.punch.TodayAttendanceSummaryResponse;
import io.github.skr416.atria.attendance.dto.punch.WebPunchRequest;
import io.github.skr416.atria.attendance.model.DailyAttendanceRecord;
import io.github.skr416.atria.attendance.model.MonthlyCloseStatus;
import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchRule;
import io.github.skr416.atria.attendance.model.PunchSource;
import io.github.skr416.atria.attendance.repository.DailyAttendanceRecordRepository;
import io.github.skr416.atria.attendance.repository.MonthlyCloseRepository;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.common.util.ClientIpResolver;
import io.github.skr416.atria.common.util.HmacSignatureVerifier;
import io.github.skr416.atria.iam.dto.dept.DepartmentTreeNodeResponse;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service managing web and bot employee clock-ins, anti-double-click defenses,
 * real-time daily summary retrieval, and monthly calendar overview queries (ADR-0003 & ADR-0008).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchService {

    private final PunchLogRepository punchLogRepository;
    private final DailyAttendanceRecordRepository dailyAttendanceRecordRepository;
    private final MonthlyCloseRepository monthlyCloseRepository;
    private final io.github.skr416.atria.attendance.repository.AttendanceRequestRepository attendanceRequestRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkScheduleResolver workScheduleResolver;
    private final AttendanceRecordCalculator calculator;

    @Value("${atria.attendance.bot.hmac-secret:}")
    private String botHmacSecret;

    /**
     * Web self-service punch for the currently authenticated employee.
     */
    @Transactional
    public TodayAttendanceSummaryResponse punchWeb(WebPunchRequest req, HttpServletRequest httpReq) {
        AtriaUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long userId = principal.getUserId();
        String clientIp = ClientIpResolver.resolve(httpReq);
        String note = req != null ? req.note() : null;

        recordPunch(userId, Instant.now(), clientIp, PunchSource.WEB, null, note);
        return getTodaySummary(userId);
    }

    /**
     * Authorized third-party Bot punch proxy endpoint protected by HMAC-SHA256 signature verification.
     */
    @Transactional
    public TodayAttendanceSummaryResponse punchBot(
            BotPunchRequest req,
            String timestampHeader,
            String signatureHeader,
            HttpServletRequest httpReq
    ) {
        if (req == null || req.username() == null || req.botClientId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (timestampHeader == null || timestampHeader.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            throw new BusinessException(ErrorCode.BOT_SIGNATURE_INVALID);
        }

        long requestEpochSeconds;
        try {
            requestEpochSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BOT_SIGNATURE_INVALID);
        }

        String canonicalPayload = req.username() + ":" + req.botClientId();
        boolean valid = HmacSignatureVerifier.verify(botHmacSecret, requestEpochSeconds, canonicalPayload, signatureHeader);
        if (!valid) {
            throw new BusinessException(ErrorCode.BOT_SIGNATURE_INVALID);
        }

        User user = userRepository.findWithAuthoritiesByUsername(req.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        Instant punchInstant = req.punchTime() != null ? req.punchTime() : Instant.now();
        String clientIp = ClientIpResolver.resolve(httpReq);

        recordPunch(user.getId(), punchInstant, clientIp, PunchSource.BOT, req.botClientId(), req.note());
        return getTodaySummary(user.getId());
    }

    /**
     * Internal atomic execution of punch log append and daily record recomputation.
     */
    private void recordPunch(
            Long userId,
            Instant punchInstant,
            String clientIp,
            PunchSource source,
            String botClientId,
            String note
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long deptId = user.getDepartmentId();
        Department dept = deptId != null ? departmentRepository.findById(deptId).orElse(null) : null;

        String calendarCode = (dept != null && dept.getCalendarCode() != null) ? dept.getCalendarCode() : "TW";
        String timezoneStr = (dept != null && dept.getTimezone() != null) ? dept.getTimezone() : "Asia/Taipei";
        ZoneId zoneId = ZoneId.of(timezoneStr);
        PunchRule rule = calculator.resolvePunchRule(calendarCode);

        LocalDate workDate = calculator.resolveLogicalWorkDate(punchInstant, zoneId, rule.getDayCutoffHour());
        String yearMonth = String.format("%04d-%02d", workDate.getYear(), workDate.getMonthValue());

        boolean isClosed = monthlyCloseRepository.existsByCalendarCodeAndYearMonthAndStatus(
                calendarCode,
                yearMonth,
                MonthlyCloseStatus.CLOSED
        );
        if (isClosed) {
            throw new BusinessException(ErrorCode.ATT_MONTHLY_PERIOD_CLOSED);
        }

        punchLogRepository.findFirstByUserIdOrderByRawPunchTimeDesc(userId).ifPresent(last -> {
            long secondsDiff = Math.abs(Duration.between(last.getRawPunchTime(), punchInstant).getSeconds());
            if (secondsDiff < rule.getMinPunchIntervalSeconds()) {
                throw new BusinessException(ErrorCode.ATT_PUNCH_DUPLICATE);
            }
        });

        PunchLog logEntity = new PunchLog();
        logEntity.setUserId(userId);
        logEntity.setDepartmentId(deptId);
        logEntity.setWorkDate(workDate);
        logEntity.setRawPunchTime(punchInstant);
        logEntity.setClientIp(clientIp != null ? clientIp : "0.0.0.0");
        logEntity.setSource(source);
        logEntity.setBotClientId(botClientId);
        logEntity.setNote(note);
        punchLogRepository.save(logEntity);

        calculator.recomputeDailyRecord(userId, workDate, false);
    }

    /**
     * Retrieves today's attendance summary for an employee, including schedule, punches, and daily record.
     */
    @Transactional(readOnly = true)
    public TodayAttendanceSummaryResponse getTodaySummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long deptId = user.getDepartmentId();
        Department dept = deptId != null ? departmentRepository.findById(deptId).orElse(null) : null;

        String calendarCode = (dept != null && dept.getCalendarCode() != null) ? dept.getCalendarCode() : "TW";
        String timezoneStr = (dept != null && dept.getTimezone() != null) ? dept.getTimezone() : "Asia/Taipei";
        ZoneId zoneId = ZoneId.of(timezoneStr);
        PunchRule rule = calculator.resolvePunchRule(calendarCode);

        Instant now = Instant.now();
        LocalDate logicalWorkDate = calculator.resolveLogicalWorkDate(now, zoneId, rule.getDayCutoffHour());
        ResolvedWorkSchedule schedule = workScheduleResolver.resolve(userId, logicalWorkDate);

        DailyAttendanceRecord record = dailyAttendanceRecordRepository
                .findByUserIdAndWorkDate(userId, logicalWorkDate)
                .orElse(null);

        List<PunchLog> logs = filterActivePunchLogs(punchLogRepository
                .findByUserIdAndWorkDateOrderByRawPunchTimeAsc(userId, logicalWorkDate));

        String yearMonth = String.format("%04d-%02d", logicalWorkDate.getYear(), logicalWorkDate.getMonthValue());
        boolean isClosed = monthlyCloseRepository.existsByCalendarCodeAndYearMonthAndStatus(
                calendarCode,
                yearMonth,
                MonthlyCloseStatus.CLOSED
        );

        Locale currentLocale = LocaleContextHolder.getLocale();
        String localizedHolidayName = schedule.holidayName() != null
                ? DepartmentTreeNodeResponse.resolveLocalizedName(schedule.holidayName(), currentLocale)
                : null;

        return TodayAttendanceSummaryResponse.builder()
                .userId(userId)
                .departmentId(deptId)
                .calendarCode(calendarCode)
                .timezone(timezoneStr)
                .serverUtcNow(now)
                .logicalWorkDate(logicalWorkDate)
                .dayCutoffHour(rule.getDayCutoffHour())
                .dayType(schedule.dayType())
                .workingDay(schedule.isWorkingDay())
                .localizedHolidayName(localizedHolidayName)
                .scheduledStartTime(schedule.workStartTime())
                .scheduledEndTime(schedule.workEndTime())
                .monthlyPeriodClosed(isClosed)
                .dailyRecord(DailyAttendanceRecordResponse.from(record))
                .punchLogs(logs.stream().map(PunchLogResponse::from).toList())
                .build();
    }

    /**
     * Retrieves monthly calendar overview, KPI summary counters, daily records, and raw punch logs.
     */
    @Transactional(readOnly = true)
    public MonthlyOverviewResponse getMonthlyOverview(Long userId, String yearMonthStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long deptId = user.getDepartmentId();
        Department dept = deptId != null ? departmentRepository.findById(deptId).orElse(null) : null;

        String calendarCode = (dept != null && dept.getCalendarCode() != null) ? dept.getCalendarCode() : "TW";
        String timezoneStr = (dept != null && dept.getTimezone() != null) ? dept.getTimezone() : "Asia/Taipei";
        ZoneId zoneId = ZoneId.of(timezoneStr);

        YearMonth ym;
        if (yearMonthStr == null || yearMonthStr.isBlank()) {
            ym = YearMonth.now(zoneId);
        } else {
            try {
                ym = YearMonth.parse(yearMonthStr.trim());
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
            }
        }
        String yearMonth = ym.toString();
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        boolean isClosed = monthlyCloseRepository.existsByCalendarCodeAndYearMonthAndStatus(
                calendarCode,
                yearMonth,
                MonthlyCloseStatus.CLOSED
        );

        Locale currentLocale = LocaleContextHolder.getLocale();
        List<WorkCalendarDayResponse> calendarDays = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            ResolvedWorkSchedule daySchedule = workScheduleResolver.resolveForCalendar(calendarCode, timezoneStr, date);
            String localizedName = daySchedule.holidayName() != null
                    ? DepartmentTreeNodeResponse.resolveLocalizedName(daySchedule.holidayName(), currentLocale)
                    : null;
            calendarDays.add(WorkCalendarDayResponse.builder()
                    .calendarCode(calendarCode)
                    .workDate(date)
                    .dayType(daySchedule.dayType())
                    .workingDay(daySchedule.isWorkingDay())
                    .workStartTime(daySchedule.workStartTime())
                    .workEndTime(daySchedule.workEndTime())
                    .breakStartTime(daySchedule.breakStartTime())
                    .breakEndTime(daySchedule.breakEndTime())
                    .holidayName(daySchedule.holidayName())
                    .localizedHolidayName(localizedName)
                    .build());
        }

        List<DailyAttendanceRecord> dailyRecords = dailyAttendanceRecordRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(userId, startDate, endDate);
        List<PunchLog> punchLogs = filterActivePunchLogs(punchLogRepository
                .findByUserIdAndWorkDateBetweenOrderByRawPunchTimeAsc(userId, startDate, endDate));

        int totalWorkedMinutes = 0;
        int normalDays = 0;
        int lateCount = 0;
        int earlyLeaveCount = 0;
        int missingPunchCount = 0;
        int absentCount = 0;

        for (DailyAttendanceRecord rec : dailyRecords) {
            totalWorkedMinutes += rec.getWorkedMinutes();
            if (rec.getStatus() != null) {
                switch (rec.getStatus()) {
                    case NORMAL -> normalDays++;
                    case LATE -> lateCount++;
                    case EARLY_LEAVE -> earlyLeaveCount++;
                    case MISSING_PUNCH -> missingPunchCount++;
                    case ABSENT -> absentCount++;
                    default -> {}
                }
            }
        }

        return MonthlyOverviewResponse.builder()
                .userId(userId)
                .calendarCode(calendarCode)
                .timezone(timezoneStr)
                .yearMonth(yearMonth)
                .monthlyClosed(isClosed)
                .totalWorkedMinutes(totalWorkedMinutes)
                .normalDays(normalDays)
                .lateCount(lateCount)
                .earlyLeaveCount(earlyLeaveCount)
                .missingPunchCount(missingPunchCount)
                .absentCount(absentCount)
                .calendarDays(calendarDays)
                .dailyRecords(dailyRecords.stream().map(DailyAttendanceRecordResponse::from).toList())
                .punchLogs(punchLogs.stream().map(PunchLogResponse::from).toList())
                .build();
    }

    private List<PunchLog> filterActivePunchLogs(List<PunchLog> rawLogs) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return List.of();
        }
        List<PunchLog> result = new ArrayList<>(rawLogs.size());
        for (PunchLog logItem : rawLogs) {
            if (logItem.getSource() == PunchSource.CORRECTION && logItem.getCorrectionRequestId() != null) {
                boolean active = attendanceRequestRepository.findById(logItem.getCorrectionRequestId())
                        .map(r -> r.getStatus() == io.github.skr416.atria.attendance.model.AttendanceRequestStatus.APPROVED)
                        .orElse(false);
                if (!active) {
                    continue;
                }
            }
            result.add(logItem);
        }
        return result;
    }
}
