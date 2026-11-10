package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.LeaveDurationResult;
import io.github.skr416.atria.attendance.model.LeaveType;
import io.github.skr416.atria.attendance.repository.LeaveTypeRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Net leave duration calculation engine intersecting requested intervals with regional work calendars,
 * deducting daily break windows (12:00–13:00), and applying ceiling rounding to the leave type's minimum unit
 * (ADR-0008 Decision 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveDurationCalculatorImpl implements LeaveDurationCalculator {

    private final WorkScheduleResolver workScheduleResolver;
    private final LeaveTypeRepository leaveTypeRepository;

    @Override
    public LeaveDurationResult calculateDuration(Long userId, String leaveTypeCode, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.ATT_INVALID_LEAVE_RANGE);
        }

        LeaveType leaveType = leaveTypeRepository.findByCode(leaveTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_TYPE_NOT_FOUND));

        ZoneId timezone;
        try {
            ResolvedWorkSchedule todaySchedule = workScheduleResolver.resolve(userId, LocalDate.now());
            timezone = (todaySchedule != null && todaySchedule.timezone() != null)
                    ? todaySchedule.timezone()
                    : ZoneId.of("Asia/Taipei");
        } catch (Exception ex) {
            timezone = ZoneId.of("Asia/Taipei");
        }

        LocalDate startDate = startTime.atZone(timezone).toLocalDate();
        LocalDate endDate = endTime.atZone(timezone).toLocalDate();

        Map<LocalDate, Integer> dailyMinutesMap = new LinkedHashMap<>();
        int rawOverlapMinutes = 0;

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            int dayMins = calculateSingleDayNetLeaveMinutes(userId, d, startTime, endTime);
            if (dayMins > 0) {
                dailyMinutesMap.put(d, dayMins);
                rawOverlapMinutes += dayMins;
            }
        }

        if (rawOverlapMinutes <= 0) {
            throw new BusinessException(ErrorCode.ATT_LEAVE_ZERO_DURATION);
        }

        int minUnit = Math.max(1, leaveType.getMinUnitMinutes());
        int roundedNetMinutes = ((rawOverlapMinutes + minUnit - 1) / minUnit) * minUnit;

        if (dailyMinutesMap.size() == 1) {
            LocalDate onlyDate = dailyMinutesMap.keySet().iterator().next();
            dailyMinutesMap.put(onlyDate, roundedNetMinutes);
        }

        return new LeaveDurationResult(
                startDate,
                endDate,
                rawOverlapMinutes,
                roundedNetMinutes,
                minUnit,
                dailyMinutesMap
        );
    }

    @Override
    public int calculateSingleDayNetLeaveMinutes(Long userId, LocalDate workDate, Instant leaveStart, Instant leaveEnd) {
        if (leaveStart == null || leaveEnd == null || !leaveEnd.isAfter(leaveStart) || workDate == null) {
            return 0;
        }

        ResolvedWorkSchedule sched = workScheduleResolver.resolve(userId, workDate);
        if (sched == null || !sched.isWorkingDay()) {
            return 0;
        }

        ZoneId tz = (sched.timezone() != null) ? sched.timezone() : ZoneId.of("Asia/Taipei");
        LocalTime workStart = (sched.workStartTime() != null) ? sched.workStartTime() : LocalTime.of(9, 0);
        LocalTime workEnd = (sched.workEndTime() != null) ? sched.workEndTime() : LocalTime.of(18, 0);

        Instant shiftStart = workDate.atTime(workStart).atZone(tz).toInstant();
        Instant shiftEnd = workDate.atTime(workEnd).atZone(tz).toInstant();

        Instant overlapStart = leaveStart.isAfter(shiftStart) ? leaveStart : shiftStart;
        Instant overlapEnd = leaveEnd.isBefore(shiftEnd) ? leaveEnd : shiftEnd;

        if (!overlapEnd.isAfter(overlapStart)) {
            return 0;
        }

        long mins = Duration.between(overlapStart, overlapEnd).toMinutes();

        LocalTime breakStart = (sched.breakStartTime() != null) ? sched.breakStartTime() : LocalTime.of(12, 0);
        LocalTime breakEnd = (sched.breakEndTime() != null) ? sched.breakEndTime() : LocalTime.of(13, 0);

        if (breakEnd.isAfter(breakStart)) {
            Instant bStart = workDate.atTime(breakStart).atZone(tz).toInstant();
            Instant bEnd = workDate.atTime(breakEnd).atZone(tz).toInstant();

            Instant bOverlapStart = overlapStart.isAfter(bStart) ? overlapStart : bStart;
            Instant bOverlapEnd = overlapEnd.isBefore(bEnd) ? overlapEnd : bEnd;

            if (bOverlapEnd.isAfter(bOverlapStart)) {
                mins -= Duration.between(bOverlapStart, bOverlapEnd).toMinutes();
            }
        }

        return (int) Math.max(0, mins);
    }
}
