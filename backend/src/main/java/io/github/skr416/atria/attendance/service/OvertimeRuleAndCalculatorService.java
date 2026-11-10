package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.overtime.OvertimeCapRuleResponse;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeCapRuleUpsertRequest;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeRateRuleResponse;
import io.github.skr416.atria.attendance.dto.overtime.OvertimeRateRuleUpsertRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.CompLeaveConversionMode;
import io.github.skr416.atria.attendance.model.OvertimeBandBreakdownItem;
import io.github.skr416.atria.attendance.model.OvertimeCapAction;
import io.github.skr416.atria.attendance.model.OvertimeCapRule;
import io.github.skr416.atria.attendance.model.OvertimeRateRule;
import io.github.skr416.atria.attendance.repository.AttendanceRequestRepository;
import io.github.skr416.atria.attendance.repository.OvertimeCapRuleRepository;
import io.github.skr416.atria.attendance.repository.OvertimeRateRuleRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing regional overtime rate and cap rules, cumulative-minute band calculations,
 * and monthly/multi-month overtime cap enforcement (ADR-0008 Decisions 5 & 6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeRuleAndCalculatorService {

    private final OvertimeRateRuleRepository overtimeRateRuleRepository;
    private final OvertimeCapRuleRepository overtimeCapRuleRepository;
    private final AttendanceRequestRepository attendanceRequestRepository;

    /**
     * Internal/public record representing the evaluated rate-multiplier breakdown of an overtime application.
     */
    public record OvertimeCalculationResult(
            int rawOvertimeMinutes,
            int totalWeightedMinutes,
            CompLeaveConversionMode effectiveCompLeaveMode,
            int grantedCompLeaveMinutes,
            int residualPayableMinutes,
            List<OvertimeBandBreakdownItem> bandBreakdown
    ) {
    }

    public List<OvertimeRateRuleResponse> listRateRules(String calendarCode) {
        List<OvertimeRateRule> rules = (calendarCode != null && !calendarCode.isBlank())
                ? overtimeRateRuleRepository.findByCalendarCodeOrderByDayTypeAscFromMinuteAsc(calendarCode)
                : overtimeRateRuleRepository.findAllByOrderByCalendarCodeAscDayTypeAscFromMinuteAsc();
        return rules.stream().map(OvertimeRateRuleResponse::from).toList();
    }

    @Transactional
    @AuditLog(action = "ATT_OT_RATE_RULE_CREATE", targetType = "OVERTIME_RATE_RULE")
    public OvertimeRateRuleResponse createRateRule(OvertimeRateRuleUpsertRequest req) {
        OvertimeRateRule rule = new OvertimeRateRule();
        rule.setCalendarCode(req.calendarCode());
        rule.setDayType(req.dayType());
        rule.setFromMinute(req.fromMinute());
        rule.setToMinute(req.toMinute());
        rule.setMultiplier(req.multiplier());
        rule.setNightPremium(req.nightPremium());
        rule.setMonthlyCumulative(req.monthlyCumulative());
        rule.setDefaultCompLeaveMode(req.defaultCompLeaveMode());
        rule.setDescription(req.description());
        OvertimeRateRule saved = overtimeRateRuleRepository.save(rule);
        return OvertimeRateRuleResponse.from(saved);
    }

    @Transactional
    @AuditLog(action = "ATT_OT_RATE_RULE_UPDATE", targetType = "OVERTIME_RATE_RULE")
    public OvertimeRateRuleResponse updateRateRule(Long id, OvertimeRateRuleUpsertRequest req) {
        OvertimeRateRule rule = overtimeRateRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_OVERTIME_RATE_RULE_NOT_FOUND));
        rule.setCalendarCode(req.calendarCode());
        rule.setDayType(req.dayType());
        rule.setFromMinute(req.fromMinute());
        rule.setToMinute(req.toMinute());
        rule.setMultiplier(req.multiplier());
        rule.setNightPremium(req.nightPremium());
        rule.setMonthlyCumulative(req.monthlyCumulative());
        rule.setDefaultCompLeaveMode(req.defaultCompLeaveMode());
        rule.setDescription(req.description());
        OvertimeRateRule saved = overtimeRateRuleRepository.save(rule);
        return OvertimeRateRuleResponse.from(saved);
    }

    public List<OvertimeCapRuleResponse> listCapRules(String calendarCode) {
        List<OvertimeCapRule> rules = (calendarCode != null && !calendarCode.isBlank())
                ? overtimeCapRuleRepository.findByCalendarCodeOrderByPeriodMonthsAscMaxMinutesAsc(calendarCode)
                : overtimeCapRuleRepository.findAllByOrderByCalendarCodeAscPeriodMonthsAscMaxMinutesAsc();
        return rules.stream()
                .map(r -> OvertimeCapRuleResponse.from(r, LocaleContextHolder.getLocale()))
                .toList();
    }

    @Transactional
    @AuditLog(action = "ATT_OT_CAP_RULE_CREATE", targetType = "OVERTIME_CAP_RULE")
    public OvertimeCapRuleResponse createCapRule(OvertimeCapRuleUpsertRequest req) {
        OvertimeCapRule rule = new OvertimeCapRule();
        rule.setCalendarCode(req.calendarCode());
        rule.setRuleCode(req.ruleCode());
        rule.setName(req.name());
        rule.setPeriodMonths(req.periodMonths());
        rule.setMaxMinutes(req.maxMinutes());
        rule.setEnforcementAction(req.enforcementAction());
        rule.setEnabled(req.enabled());
        rule.setDescription(req.description());
        OvertimeCapRule saved = overtimeCapRuleRepository.save(rule);
        return OvertimeCapRuleResponse.from(saved, LocaleContextHolder.getLocale());
    }

    @Transactional
    @AuditLog(action = "ATT_OT_CAP_RULE_UPDATE", targetType = "OVERTIME_CAP_RULE")
    public OvertimeCapRuleResponse updateCapRule(Long id, OvertimeCapRuleUpsertRequest req) {
        OvertimeCapRule rule = overtimeCapRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_OVERTIME_CAP_RULE_NOT_FOUND));
        rule.setCalendarCode(req.calendarCode());
        rule.setRuleCode(req.ruleCode());
        rule.setName(req.name());
        rule.setPeriodMonths(req.periodMonths());
        rule.setMaxMinutes(req.maxMinutes());
        rule.setEnforcementAction(req.enforcementAction());
        rule.setEnabled(req.enabled());
        rule.setDescription(req.description());
        OvertimeCapRule saved = overtimeCapRuleRepository.save(rule);
        return OvertimeCapRuleResponse.from(saved, LocaleContextHolder.getLocale());
    }

    /**
     * Evaluates an overtime application into exact rate-multiplier bands and determines compensatory leave minutes
     * using employee-favorable CEILING rounding (ADR-0008 Decision 5).
     */
    public OvertimeCalculationResult calculateOvertime(
            String calendarCode,
            CalendarDayType dayType,
            int overtimeMinutes,
            CompLeaveConversionMode requestedMode
    ) {
        if (overtimeMinutes <= 0) {
            throw new BusinessException(ErrorCode.ATT_INVALID_OVERTIME_RANGE);
        }

        List<OvertimeRateRule> bands = overtimeRateRuleRepository
                .findByCalendarCodeAndDayTypeOrderByFromMinuteAsc(calendarCode, dayType)
                .stream()
                .filter(r -> !r.isNightPremium() && !r.isMonthlyCumulative())
                .toList();

        if (bands.isEmpty()) {
            throw new BusinessException(ErrorCode.ATT_INVALID_OVERTIME_RANGE);
        }

        int highestToMinute = bands.stream().mapToInt(OvertimeRateRule::getToMinute).max().orElse(0);
        if (overtimeMinutes > highestToMinute) {
            throw new BusinessException(ErrorCode.ATT_INVALID_OVERTIME_RANGE);
        }

        List<OvertimeBandBreakdownItem> bandBreakdown = new ArrayList<>();
        BigDecimal exactTotalWeighted = BigDecimal.ZERO;

        for (OvertimeRateRule band : bands) {
            int bandStart = Math.max(0, band.getFromMinute());
            int bandEnd = Math.min(overtimeMinutes, band.getToMinute());
            if (bandEnd > bandStart) {
                int minsInBand = bandEnd - bandStart;
                BigDecimal exactWeighted = BigDecimal.valueOf(minsInBand).multiply(band.getMultiplier());
                int bandWeighted = exactWeighted.setScale(0, RoundingMode.CEILING).intValueExact();
                bandBreakdown.add(new OvertimeBandBreakdownItem(
                        band.getFromMinute(),
                        band.getToMinute(),
                        minsInBand,
                        band.getMultiplier().toPlainString(),
                        bandWeighted
                ));
                exactTotalWeighted = exactTotalWeighted.add(exactWeighted);
            }
        }

        int totalWeightedMinutes = exactTotalWeighted.setScale(0, RoundingMode.CEILING).intValueExact();
        if (totalWeightedMinutes < overtimeMinutes) {
            throw new BusinessException(ErrorCode.ATT_COMP_LEAVE_RATIO_BELOW_ONE);
        }

        CompLeaveConversionMode mode = requestedMode != null
                ? requestedMode
                : bands.get(0).getDefaultCompLeaveMode();

        int grantedCompLeaveMinutes;
        int residualPayableMinutes;

        if (mode == CompLeaveConversionMode.RATE_MULTIPLIED) {
            grantedCompLeaveMinutes = totalWeightedMinutes;
            residualPayableMinutes = 0;
        } else {
            grantedCompLeaveMinutes = overtimeMinutes;
            residualPayableMinutes = Math.max(0, totalWeightedMinutes - overtimeMinutes);
        }

        return new OvertimeCalculationResult(
                overtimeMinutes,
                totalWeightedMinutes,
                mode,
                grantedCompLeaveMinutes,
                residualPayableMinutes,
                bandBreakdown
        );
    }

    /**
     * Checks enabled regional overtime caps for an employee and returns warnings or throws BLOCK exception
     * (ADR-0008 Decision 5).
     */
    public List<String> checkOvertimeCaps(
            Long userId,
            String calendarCode,
            LocalDate workDate,
            int newOvertimeMinutes
    ) {
        List<OvertimeCapRule> capRules = overtimeCapRuleRepository
                .findByCalendarCodeAndEnabledTrueOrderByPeriodMonthsAscMaxMinutesAsc(calendarCode);

        List<String> warnings = new ArrayList<>();
        YearMonth targetYm = YearMonth.from(workDate);

        for (OvertimeCapRule cap : capRules) {
            LocalDate windowStart = targetYm.minusMonths(cap.getPeriodMonths() - 1L).atDay(1);
            LocalDate windowEnd = targetYm.atEndOfMonth();

            List<AttendanceRequest> existing = attendanceRequestRepository
                    .findByUserIdAndRequestTypeAndStatusInAndStartDateBetween(
                            userId,
                            AttendanceRequestType.OVERTIME,
                            List.of(AttendanceRequestStatus.PENDING, AttendanceRequestStatus.APPROVED),
                            windowStart,
                            windowEnd
                    );

            int existingMinutes = existing.stream().mapToInt(AttendanceRequest::getRequestedMinutes).sum();
            int total = existingMinutes + newOvertimeMinutes;

            if (total > cap.getMaxMinutes()) {
                if (cap.getEnforcementAction() == OvertimeCapAction.BLOCK) {
                    throw new BusinessException(ErrorCode.ATT_OVERTIME_CAP_EXCEEDED);
                } else if (cap.getEnforcementAction() == OvertimeCapAction.WARN) {
                    warnings.add(cap.getRuleCode() + " (" + total + "/" + cap.getMaxMinutes() + " mins)");
                }
            }
        }

        return warnings;
    }
}
