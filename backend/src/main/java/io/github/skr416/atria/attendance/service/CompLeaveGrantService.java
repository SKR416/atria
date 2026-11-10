package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.overtime.CompLeaveExpirySettlementResultDto;
import io.github.skr416.atria.attendance.dto.overtime.CompLeaveGrantResponse;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.CalendarDayType;
import io.github.skr416.atria.attendance.model.CompLeaveGrant;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.model.OvertimeBandBreakdownItem;
import io.github.skr416.atria.attendance.repository.CompLeaveGrantRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service managing compensatory leave grants converted from approved overtime,
 * linked leave balance buckets, and Taiwan LSA Art. 32-1 expiry cash settlements (ADR-0008 Decision 6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompLeaveGrantService {

    private final CompLeaveGrantRepository compLeaveGrantRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceLedger leaveBalanceLedger;
    private final OvertimeRuleAndCalculatorService overtimeRuleAndCalculatorService;

    /**
     * Converts an approved overtime request into a compensatory leave grant and creates a linked
     * FIFO leave balance bucket.
     */
    @Transactional
    public CompLeaveGrant grantFromApprovedOvertime(AttendanceRequest request, CalendarDayType dayType) {
        var calc = overtimeRuleAndCalculatorService.calculateOvertime(
                request.getCalendarCode(),
                dayType,
                request.getRequestedMinutes(),
                request.getCompLeaveMode()
        );

        CompLeaveGrant grant = new CompLeaveGrant();
        grant.setUserId(request.getUserId());
        grant.setCalendarCode(request.getCalendarCode());
        grant.setSourceRequestId(request.getId());
        grant.setWorkDate(request.getStartDate());
        grant.setDayType(dayType);
        grant.setCompLeaveMode(calc.effectiveCompLeaveMode());
        grant.setOriginalOvertimeMinutes(request.getRequestedMinutes());
        grant.setGrantedMinutes(calc.grantedCompLeaveMinutes());
        grant.setResidualPayableMinutes(calc.residualPayableMinutes());
        grant.setBandBreakdownJson(calc.bandBreakdown());
        grant.setGrantedAt(Instant.now());
        grant.setExpiresAt(request.getStartDate().plusMonths(6));
        grant.setExpiredSettled(false);
        grant.setExpiredPayableWeightedMinutes(0);

        CompLeaveGrant savedGrant = compLeaveGrantRepository.save(grant);

        leaveBalanceLedger.createBucket(
                request.getUserId(),
                "COMP_LEAVE",
                request.getCalendarCode(),
                request.getStartDate().getYear(),
                savedGrant.getGrantedMinutes(),
                request.getStartDate(),
                savedGrant.getExpiresAt(),
                savedGrant.getId(),
                0,
                "Comp leave from OT request #" + request.getId()
        );

        return savedGrant;
    }

    /**
     * Lists compensatory leave grants for a user (or all if userId is null) populated with live bucket balance figures.
     */
    public List<CompLeaveGrantResponse> listGrants(Long userId) {
        List<CompLeaveGrant> grants = (userId != null)
                ? compLeaveGrantRepository.findByUserIdOrderByExpiresAtAscIdAsc(userId)
                : compLeaveGrantRepository.findAll().stream()
                .sorted(Comparator.comparing(CompLeaveGrant::getExpiresAt).thenComparing(CompLeaveGrant::getId))
                .toList();

        return grants.stream().map(g -> {
            LeaveBalance b = leaveBalanceRepository.findBySourceCompGrantId(g.getId()).orElse(null);
            int used = b != null ? b.getUsedMinutes() : 0;
            int pending = b != null ? b.getPendingMinutes() : 0;
            int available = b != null ? b.getAvailableMinutes() : 0;
            return new CompLeaveGrantResponse(
                    g.getId(),
                    g.getUserId(),
                    g.getCalendarCode(),
                    g.getSourceRequestId(),
                    g.getWorkDate(),
                    g.getDayType(),
                    g.getCompLeaveMode(),
                    g.getOriginalOvertimeMinutes(),
                    g.getGrantedMinutes(),
                    used,
                    pending,
                    available,
                    g.getResidualPayableMinutes(),
                    g.getBandBreakdownJson(),
                    g.getGrantedAt(),
                    g.getExpiresAt(),
                    g.isExpiredSettled(),
                    g.getExpiredPayableWeightedMinutes()
            );
        }).toList();
    }

    /**
     * Settles an expired compensatory leave grant by converting unused minutes back to original overtime band
     * minutes and payable weighted hours without double-multiplication drift (Taiwan LSA Art. 32-1, ADR-0008 Decision 6).
     */
    @Transactional
    @AuditLog(action = "ATT_COMP_LEAVE_EXPIRY_SETTLE", targetType = "COMP_LEAVE_GRANT")
    public CompLeaveExpirySettlementResultDto settleExpiredGrant(Long grantId) {
        CompLeaveGrant grant = compLeaveGrantRepository.findById(grantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_COMP_GRANT_NOT_FOUND));

        if (grant.isExpiredSettled()) {
            throw new BusinessException(ErrorCode.ATT_REQUEST_INVALID_STATE_TRANSITION);
        }

        LeaveBalance bucket = leaveBalanceRepository.findBySourceCompGrantId(grant.getId()).orElse(null);
        if (bucket != null && bucket.getPendingMinutes() > 0) {
            throw new BusinessException(ErrorCode.ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE);
        }

        int granted = grant.getGrantedMinutes();
        int used = bucket != null ? bucket.getUsedMinutes() : 0;
        int unused = Math.max(0, granted - used);

        if (unused == 0) {
            grant.setExpiredSettled(true);
            grant.setExpiredPayableWeightedMinutes(0);
            compLeaveGrantRepository.save(grant);
            return new CompLeaveExpirySettlementResultDto(
                    grant.getId(),
                    bucket != null ? bucket.getId() : null,
                    grant.getUserId(),
                    granted,
                    used,
                    0,
                    0,
                    0,
                    List.of()
            );
        }

        List<OvertimeBandBreakdownItem> originalBands = grant.getBandBreakdownJson() != null
                ? grant.getBandBreakdownJson()
                : List.of();
        List<OvertimeBandBreakdownItem> restoredBands = new ArrayList<>();
        int restoredOriginalOvertimeMinutes = 0;
        int payableWeightedMinutes = 0;

        for (OvertimeBandBreakdownItem band : originalBands) {
            BigDecimal bandOrig = BigDecimal.valueOf(band.minutes())
                    .multiply(BigDecimal.valueOf(unused))
                    .divide(BigDecimal.valueOf(granted), 4, RoundingMode.HALF_UP);
            int restoredBandOrigMins = bandOrig.setScale(0, RoundingMode.CEILING).intValueExact();
            BigDecimal mult = new BigDecimal(band.multiplier());
            int restoredBandWeighted = BigDecimal.valueOf(restoredBandOrigMins)
                    .multiply(mult)
                    .setScale(0, RoundingMode.CEILING)
                    .intValueExact();

            restoredBands.add(new OvertimeBandBreakdownItem(
                    band.fromMinute(),
                    band.toMinute(),
                    restoredBandOrigMins,
                    band.multiplier(),
                    restoredBandWeighted
            ));
            restoredOriginalOvertimeMinutes += restoredBandOrigMins;
            payableWeightedMinutes += restoredBandWeighted;
        }

        grant.setExpiredSettled(true);
        grant.setExpiredPayableWeightedMinutes(payableWeightedMinutes);
        compLeaveGrantRepository.save(grant);

        if (bucket != null) {
            bucket.setUsedMinutes(bucket.getTotalMinutes());
            leaveBalanceRepository.save(bucket);
        }

        return new CompLeaveExpirySettlementResultDto(
                grant.getId(),
                bucket != null ? bucket.getId() : null,
                grant.getUserId(),
                granted,
                used,
                unused,
                restoredOriginalOvertimeMinutes,
                payableWeightedMinutes,
                restoredBands
        );
    }
}
