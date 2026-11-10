package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.AnnualLeaveGrantBatchResultDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveBalanceBucketDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeResponse;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.model.LeaveGrantRule;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.attendance.repository.LeaveGrantRuleRepository;
import io.github.skr416.atria.attendance.repository.LeaveTypeRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing seniority-based statutory annual leave grants for Taiwan (Art. 38) and
 * Japan (Art. 39) anniversary cycles with idempotent deduplication (ADR-0008 Decisions 4 & 11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualLeaveGrantService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveGrantRuleRepository leaveGrantRuleRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceLedger leaveBalanceLedger;
    private final LeaveTypeRepository leaveTypeRepository;

    /**
     * Executes statutory annual leave calculation and grant allocation for all active employees as of {@code asOfDate}.
     */
    @Transactional
    @AuditLog(action = "ATT_ANNUAL_LEAVE_GRANT_BATCH", targetType = "LEAVE_BALANCE")
    public AnnualLeaveGrantBatchResultDto grantAnnualLeaveForAllUsers(LocalDate asOfDate) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        List<User> users = userRepository.findAll();

        int grantedCount = 0;
        int skippedAlreadyGrantedCount = 0;
        List<String> skippedMissingHireDateUsernames = new ArrayList<>();
        List<LeaveBalanceBucketDto> grantedBuckets = new ArrayList<>();

        for (User user : users) {
            if (!user.isEnabled() || user.isDeleted()) {
                continue;
            }
            if (user.getHireDate() == null) {
                skippedMissingHireDateUsernames.add(user.getUsername());
                continue;
            }

            long tenureMonths = ChronoUnit.MONTHS.between(user.getHireDate(), effectiveAsOf);
            if (tenureMonths < 6) {
                continue;
            }

            String calendarCode = resolveUserCalendarCode(user);
            LeaveGrantRule rule = findMatchingGrantRule(calendarCode, tenureMonths);
            if (rule == null) {
                continue;
            }

            int grantDays = computeGrantDays(calendarCode, tenureMonths, rule);
            int grantMinutes = grantDays * 480;

            GrantCycle cycle = computeGrantCycle(user.getHireDate(), effectiveAsOf, tenureMonths, calendarCode, rule);

            List<LeaveBalance> existingBuckets = leaveBalanceRepository
                    .findByUserIdAndLeaveTypeCodeOrderByValidUntilAscIdAsc(user.getId(), "ANNUAL");
            boolean alreadyGranted = existingBuckets.stream()
                    .anyMatch(b -> cycle.validFrom().equals(b.getValidFrom()));
            if (alreadyGranted) {
                skippedAlreadyGrantedCount++;
                continue;
            }

            int mandatoryTargetMinutes = ("JP".equalsIgnoreCase(calendarCode) && grantMinutes >= 4800) ? 2400 : 0;

            LeaveBalance created = leaveBalanceLedger.createBucket(
                    user.getId(),
                    "ANNUAL",
                    calendarCode,
                    cycle.grantYear(),
                    grantMinutes,
                    cycle.validFrom(),
                    cycle.validUntil(),
                    null,
                    mandatoryTargetMinutes,
                    "Statutory annual leave (" + grantDays + "d)"
            );

            grantedCount++;
            grantedBuckets.add(LeaveBalanceBucketDto.from(created, getLocalizedLeaveTypeName("ANNUAL")));
        }

        return new AnnualLeaveGrantBatchResultDto(
                effectiveAsOf,
                grantedCount,
                skippedAlreadyGrantedCount,
                skippedMissingHireDateUsernames,
                grantedBuckets
        );
    }

    /**
     * Evaluates and grants annual leave for a single employee as of {@code asOfDate}.
     * Throws {@code ATT_USER_HIRE_DATE_MISSING} (2027) if hire date is absent.
     */
    @Transactional
    public LeaveBalanceBucketDto grantAnnualLeaveForUser(Long userId, LocalDate asOfDate) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getHireDate() == null) {
            throw new BusinessException(ErrorCode.ATT_USER_HIRE_DATE_MISSING);
        }

        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        long tenureMonths = ChronoUnit.MONTHS.between(user.getHireDate(), effectiveAsOf);
        if (tenureMonths < 6) {
            return null;
        }

        String calendarCode = resolveUserCalendarCode(user);
        LeaveGrantRule rule = findMatchingGrantRule(calendarCode, tenureMonths);
        if (rule == null) {
            return null;
        }

        int grantDays = computeGrantDays(calendarCode, tenureMonths, rule);
        int grantMinutes = grantDays * 480;

        GrantCycle cycle = computeGrantCycle(user.getHireDate(), effectiveAsOf, tenureMonths, calendarCode, rule);

        List<LeaveBalance> existingBuckets = leaveBalanceRepository
                .findByUserIdAndLeaveTypeCodeOrderByValidUntilAscIdAsc(user.getId(), "ANNUAL");
        for (LeaveBalance b : existingBuckets) {
            if (cycle.validFrom().equals(b.getValidFrom())) {
                return LeaveBalanceBucketDto.from(b, getLocalizedLeaveTypeName("ANNUAL"));
            }
        }

        int mandatoryTargetMinutes = ("JP".equalsIgnoreCase(calendarCode) && grantMinutes >= 4800) ? 2400 : 0;

        LeaveBalance created = leaveBalanceLedger.createBucket(
                user.getId(),
                "ANNUAL",
                calendarCode,
                cycle.grantYear(),
                grantMinutes,
                cycle.validFrom(),
                cycle.validUntil(),
                null,
                mandatoryTargetMinutes,
                "Statutory annual leave (" + grantDays + "d)"
        );

        return LeaveBalanceBucketDto.from(created, getLocalizedLeaveTypeName("ANNUAL"));
    }

    private record GrantCycle(LocalDate validFrom, LocalDate validUntil, int grantYear) {
    }

    private GrantCycle computeGrantCycle(
            LocalDate hireDate,
            LocalDate asOfDate,
            long tenureMonths,
            String calendarCode,
            LeaveGrantRule rule
    ) {
        int validMonths = rule.getValidMonths();
        if (rule.getGrantBasis() == io.github.skr416.atria.attendance.model.LeaveGrantBasis.CALENDAR_YEAR) {
            LocalDate validFrom = LocalDate.of(asOfDate.getYear(), 1, 1);
            LocalDate validUntil = validFrom.plusMonths(validMonths).minusDays(1);
            return new GrantCycle(validFrom, validUntil, validFrom.getYear());
        }
        LocalDate validFrom;
        if (tenureMonths < 12) {
            validFrom = hireDate.plusMonths(6);
        } else {
            if ("JP".equalsIgnoreCase(calendarCode)) {
                long halfYearSteps = (tenureMonths - 6) / 12;
                validFrom = hireDate.plusMonths(6 + halfYearSteps * 12);
            } else {
                int fullYears = (int) (tenureMonths / 12);
                validFrom = hireDate.plusYears(fullYears);
            }
        }
        LocalDate validUntil = validFrom.plusMonths(validMonths).minusDays(1);
        int grantYear = validFrom.getYear();
        return new GrantCycle(validFrom, validUntil, grantYear);
    }

    private int computeGrantDays(String calendarCode, long tenureMonths, LeaveGrantRule rule) {
        int grantDays = rule.getGrantDays();
        if ("TW".equalsIgnoreCase(calendarCode) && tenureMonths >= 120 && rule.getMaxTenureMonths() == null) {
            int fullYears = (int) (tenureMonths / 12);
            grantDays = Math.min(30, 15 + (fullYears - 9));
        }
        return grantDays;
    }

    private LeaveGrantRule findMatchingGrantRule(String calendarCode, long tenureMonths) {
        List<LeaveGrantRule> rules = leaveGrantRuleRepository.findByCalendarCodeOrderByMinTenureMonthsAsc(calendarCode);
        return rules.stream()
                .filter(r -> "ANNUAL".equalsIgnoreCase(r.getLeaveTypeCode()))
                .filter(r -> r.getMinTenureMonths() <= tenureMonths
                        && (r.getMaxTenureMonths() == null || tenureMonths < r.getMaxTenureMonths()))
                .findFirst()
                .orElse(null);
    }

    private String resolveUserCalendarCode(User user) {
        if (user.getDepartmentId() != null) {
            return departmentRepository.findById(user.getDepartmentId())
                    .map(Department::getCalendarCode)
                    .filter(c -> !c.isBlank())
                    .orElse("TW");
        }
        return "TW";
    }

    private String getLocalizedLeaveTypeName(String code) {
        return leaveTypeRepository.findByCode(code)
                .map(t -> LeaveTypeResponse.from(t, LocaleContextHolder.getLocale()).localizedName())
                .orElse(code);
    }
}
