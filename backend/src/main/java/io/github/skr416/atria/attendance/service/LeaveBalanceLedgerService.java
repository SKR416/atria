package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.ExpiringCompLeaveDto;
import io.github.skr416.atria.attendance.dto.leave.JpFiveDayObligationDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveBalanceBucketDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveDashboardResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeBalanceSummaryDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeResponse;
import io.github.skr416.atria.attendance.dto.leave.ManualLeaveGrantRequest;
import io.github.skr416.atria.attendance.model.CompLeaveGrant;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.model.LeaveBalanceTx;
import io.github.skr416.atria.attendance.model.LeaveBalanceTxType;
import io.github.skr416.atria.attendance.model.LeaveType;
import io.github.skr416.atria.attendance.repository.CompLeaveGrantRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceRepository;
import io.github.skr416.atria.attendance.repository.LeaveBalanceTxRepository;
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
import java.util.Locale;

/**
 * Service implementing FIFO leave balance ledger mutations and employee balance dashboard queries (ADR-0008 Decisions 4 & 8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceLedgerService implements LeaveBalanceLedger, LeaveDashboardQuery {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceTxRepository leaveBalanceTxRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final CompLeaveGrantRepository compLeaveGrantRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public void freeze(Long requestId, Long userId, String leaveTypeCode, int minutes, LocalDate referenceDate) {
        if (minutes <= 0) {
            return;
        }
        LeaveType leaveType = leaveTypeRepository.findByCode(leaveTypeCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_TYPE_NOT_FOUND));

        if (!leaveType.isBalanceTracked()) {
            return;
        }

        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        List<LeaveBalance> buckets = leaveBalanceRepository
                .findByUserIdAndLeaveTypeCodeAndValidFromLessThanEqualAndValidUntilGreaterThanEqualOrderByValidUntilAscIdAsc(
                        userId, leaveTypeCode, refDate, refDate
                );

        int totalAvailable = buckets.stream().mapToInt(LeaveBalance::getAvailableMinutes).sum();
        if (totalAvailable < minutes) {
            throw new BusinessException(ErrorCode.ATT_INSUFFICIENT_LEAVE_BALANCE);
        }

        int remainingToFreeze = minutes;
        for (LeaveBalance bucket : buckets) {
            int avail = bucket.getAvailableMinutes();
            if (avail <= 0) {
                continue;
            }
            int deduct = Math.min(remainingToFreeze, avail);
            bucket.setPendingMinutes(bucket.getPendingMinutes() + deduct);
            leaveBalanceRepository.save(bucket);

            LeaveBalanceTx tx = new LeaveBalanceTx();
            tx.setRequestId(requestId);
            tx.setBalanceId(bucket.getId());
            tx.setUserId(userId);
            tx.setLeaveTypeCode(leaveTypeCode);
            tx.setTxType(LeaveBalanceTxType.FREEZE);
            tx.setMinutes(deduct);
            leaveBalanceTxRepository.save(tx);

            remainingToFreeze -= deduct;
            if (remainingToFreeze == 0) {
                break;
            }
        }
    }

    @Override
    @Transactional
    public void commit(Long requestId) {
        if (requestId == null) {
            return;
        }
        List<LeaveBalanceTx> existingCommits = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.COMMIT
        );
        if (!existingCommits.isEmpty()) {
            return;
        }

        List<LeaveBalanceTx> freezeTxs = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.FREEZE
        );
        for (LeaveBalanceTx tx : freezeTxs) {
            LeaveBalance bucket = leaveBalanceRepository.findById(tx.getBalanceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
            bucket.setPendingMinutes(Math.max(0, bucket.getPendingMinutes() - tx.getMinutes()));
            bucket.setUsedMinutes(bucket.getUsedMinutes() + tx.getMinutes());
            leaveBalanceRepository.save(bucket);

            LeaveBalanceTx commitTx = new LeaveBalanceTx();
            commitTx.setRequestId(requestId);
            commitTx.setBalanceId(bucket.getId());
            commitTx.setUserId(tx.getUserId());
            commitTx.setLeaveTypeCode(tx.getLeaveTypeCode());
            commitTx.setTxType(LeaveBalanceTxType.COMMIT);
            commitTx.setMinutes(tx.getMinutes());
            leaveBalanceTxRepository.save(commitTx);
        }
    }

    @Override
    @Transactional
    public void restoreFromPending(Long requestId) {
        if (requestId == null) {
            return;
        }
        List<LeaveBalanceTx> existingRestores = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.RESTORE_PENDING
        );
        if (!existingRestores.isEmpty()) {
            return;
        }

        List<LeaveBalanceTx> freezeTxs = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.FREEZE
        );
        for (LeaveBalanceTx tx : freezeTxs) {
            LeaveBalance bucket = leaveBalanceRepository.findById(tx.getBalanceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
            bucket.setPendingMinutes(Math.max(0, bucket.getPendingMinutes() - tx.getMinutes()));
            leaveBalanceRepository.save(bucket);

            LeaveBalanceTx restoreTx = new LeaveBalanceTx();
            restoreTx.setRequestId(requestId);
            restoreTx.setBalanceId(bucket.getId());
            restoreTx.setUserId(tx.getUserId());
            restoreTx.setLeaveTypeCode(tx.getLeaveTypeCode());
            restoreTx.setTxType(LeaveBalanceTxType.RESTORE_PENDING);
            restoreTx.setMinutes(tx.getMinutes());
            leaveBalanceTxRepository.save(restoreTx);
        }
    }

    @Override
    @Transactional
    public void restoreFromUsed(Long requestId) {
        if (requestId == null) {
            return;
        }
        List<LeaveBalanceTx> existingRestores = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.RESTORE_USED
        );
        if (!existingRestores.isEmpty()) {
            return;
        }

        List<LeaveBalanceTx> commitTxs = leaveBalanceTxRepository.findByRequestIdAndTxTypeOrderByIdAsc(
                requestId, LeaveBalanceTxType.COMMIT
        );
        for (LeaveBalanceTx tx : commitTxs) {
            LeaveBalance bucket = leaveBalanceRepository.findById(tx.getBalanceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
            bucket.setUsedMinutes(Math.max(0, bucket.getUsedMinutes() - tx.getMinutes()));
            leaveBalanceRepository.save(bucket);

            LeaveBalanceTx restoreTx = new LeaveBalanceTx();
            restoreTx.setRequestId(requestId);
            restoreTx.setBalanceId(bucket.getId());
            restoreTx.setUserId(tx.getUserId());
            restoreTx.setLeaveTypeCode(tx.getLeaveTypeCode());
            restoreTx.setTxType(LeaveBalanceTxType.RESTORE_USED);
            restoreTx.setMinutes(tx.getMinutes());
            leaveBalanceTxRepository.save(restoreTx);
        }
    }

    @Override
    @Transactional
    public LeaveBalance createBucket(
            Long userId,
            String leaveTypeCode,
            String calendarCode,
            int grantYear,
            int totalMinutes,
            LocalDate validFrom,
            LocalDate validUntil,
            Long sourceCompGrantId,
            int mandatoryTargetMinutes,
            String note
    ) {
        LeaveBalance bucket = new LeaveBalance();
        bucket.setUserId(userId);
        bucket.setLeaveTypeCode(leaveTypeCode);
        bucket.setCalendarCode(calendarCode != null ? calendarCode : "TW");
        bucket.setGrantYear(grantYear);
        bucket.setTotalMinutes(totalMinutes);
        bucket.setUsedMinutes(0);
        bucket.setPendingMinutes(0);
        bucket.setValidFrom(validFrom);
        bucket.setValidUntil(validUntil);
        bucket.setSourceCompGrantId(sourceCompGrantId);
        bucket.setMandatoryTargetMinutes(mandatoryTargetMinutes);
        bucket.setNote(note);
        return leaveBalanceRepository.save(bucket);
    }

    @Override
    @Transactional
    public void revokeCompGrantBucket(Long sourceCompGrantId) {
        if (sourceCompGrantId == null) {
            return;
        }
        LeaveBalance bucket = leaveBalanceRepository.findBySourceCompGrantId(sourceCompGrantId).orElse(null);
        if (bucket != null) {
            if (bucket.getUsedMinutes() > 0 || bucket.getPendingMinutes() > 0) {
                throw new BusinessException(ErrorCode.ATT_COMP_LEAVE_ALREADY_USED_CANNOT_REVOKE);
            }
            bucket.setDeleted(true);
            leaveBalanceRepository.save(bucket);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveDashboardResponse getDashboard(Long userId, LocalDate asOfDate) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String calendarCode = "TW";
        if (user.getDepartmentId() != null) {
            calendarCode = departmentRepository.findById(user.getDepartmentId())
                    .map(Department::getCalendarCode)
                    .filter(c -> !c.isBlank())
                    .orElse("TW");
        }

        List<LeaveBalance> allBuckets = leaveBalanceRepository.findByUserIdOrderByValidUntilAscIdAsc(userId);
        List<LeaveType> leaveTypes = leaveTypeRepository.findAllByOrderBySortOrderAscIdAsc();
        Locale locale = LocaleContextHolder.getLocale();

        List<LeaveTypeBalanceSummaryDto> balances = new ArrayList<>();
        for (LeaveType type : leaveTypes) {
            String localizedTypeName = LeaveTypeResponse.from(type, locale).localizedName();
            List<LeaveBalance> activeBuckets = allBuckets.stream()
                    .filter(b -> type.getCode().equalsIgnoreCase(b.getLeaveTypeCode())
                            && !b.getValidUntil().isBefore(effectiveAsOf))
                    .toList();

            int totalMinutes = activeBuckets.stream().mapToInt(LeaveBalance::getTotalMinutes).sum();
            int usedMinutes = activeBuckets.stream().mapToInt(LeaveBalance::getUsedMinutes).sum();
            int pendingMinutes = activeBuckets.stream().mapToInt(LeaveBalance::getPendingMinutes).sum();
            int availableMinutes = activeBuckets.stream().mapToInt(LeaveBalance::getAvailableMinutes).sum();

            List<LeaveBalanceBucketDto> bucketDtos = activeBuckets.stream()
                    .map(b -> LeaveBalanceBucketDto.from(b, localizedTypeName))
                    .toList();

            balances.add(new LeaveTypeBalanceSummaryDto(
                    type.getCode(),
                    localizedTypeName,
                    type.getMinUnitMinutes(),
                    type.isPaid(),
                    type.isBalanceTracked(),
                    totalMinutes,
                    usedMinutes,
                    pendingMinutes,
                    availableMinutes,
                    bucketDtos
            ));
        }

        LocalDate ninetyDaysLater = effectiveAsOf.plusDays(90);
        List<ExpiringCompLeaveDto> expiringCompLeaves = new ArrayList<>();
        for (LeaveBalance b : allBuckets) {
            if ("COMP_LEAVE".equalsIgnoreCase(b.getLeaveTypeCode())
                    && b.getAvailableMinutes() > 0
                    && !b.getValidUntil().isBefore(effectiveAsOf)
                    && !b.getValidUntil().isAfter(ninetyDaysLater)) {
                LocalDate workDate = b.getValidFrom();
                if (b.getSourceCompGrantId() != null) {
                    workDate = compLeaveGrantRepository.findById(b.getSourceCompGrantId())
                            .map(CompLeaveGrant::getWorkDate)
                            .orElse(b.getValidFrom());
                }
                long daysUntil = ChronoUnit.DAYS.between(effectiveAsOf, b.getValidUntil());
                expiringCompLeaves.add(new ExpiringCompLeaveDto(
                        b.getId(),
                        b.getSourceCompGrantId(),
                        workDate,
                        b.getAvailableMinutes(),
                        b.getValidUntil(),
                        daysUntil
                ));
            }
        }

        JpFiveDayObligationDto jpObligation;
        if ("JP".equalsIgnoreCase(calendarCode)) {
            List<LeaveBalance> activeAnnual = allBuckets.stream()
                    .filter(b -> "ANNUAL".equalsIgnoreCase(b.getLeaveTypeCode()) && !b.getValidUntil().isBefore(effectiveAsOf))
                    .toList();
            boolean applicable = activeAnnual.stream()
                    .anyMatch(b -> b.getMandatoryTargetMinutes() > 0 || b.getTotalMinutes() >= 4800);
            if (applicable) {
                int targetMinutes = 2400;
                int usedMinutes = activeAnnual.stream().mapToInt(LeaveBalance::getUsedMinutes).sum();
                int pendingMinutes = activeAnnual.stream().mapToInt(LeaveBalance::getPendingMinutes).sum();
                int remaining = Math.max(0, targetMinutes - usedMinutes);
                boolean fulfilled = remaining == 0;
                LocalDate deadlineDate = activeAnnual.stream()
                        .filter(b -> b.getMandatoryTargetMinutes() > 0 || b.getTotalMinutes() >= 4800)
                        .map(b -> b.getValidFrom().plusYears(1).minusDays(1))
                        .min(LocalDate::compareTo)
                        .orElse(effectiveAsOf.plusYears(1));
                jpObligation = new JpFiveDayObligationDto(
                        true,
                        targetMinutes,
                        usedMinutes,
                        pendingMinutes,
                        remaining,
                        deadlineDate,
                        fulfilled
                );
            } else {
                jpObligation = new JpFiveDayObligationDto(false, 0, 0, 0, 0, null, false);
            }
        } else {
            jpObligation = new JpFiveDayObligationDto(false, 0, 0, 0, 0, null, false);
        }

        return new LeaveDashboardResponse(
                userId,
                calendarCode,
                effectiveAsOf,
                balances,
                expiringCompLeaves,
                jpObligation
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveBalanceBucketDto> listUserBuckets(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<LeaveBalance> buckets = leaveBalanceRepository.findByUserIdOrderByValidUntilAscIdAsc(userId);
        return buckets.stream()
                .map(b -> LeaveBalanceBucketDto.from(b, getLocalizedLeaveTypeName(b.getLeaveTypeCode())))
                .toList();
    }

    private String getLocalizedLeaveTypeName(String code) {
        return leaveTypeRepository.findByCode(code)
                .map(t -> LeaveTypeResponse.from(t, LocaleContextHolder.getLocale()).localizedName())
                .orElse(code);
    }
}
