package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.close.BlockingPendingRequestDto;
import io.github.skr416.atria.attendance.dto.close.MonthlyCloseRequest;
import io.github.skr416.atria.attendance.dto.close.MonthlyCloseResponse;
import io.github.skr416.atria.attendance.dto.close.MonthlyReopenRequest;
import io.github.skr416.atria.attendance.model.MonthlyClose;
import io.github.skr416.atria.attendance.model.MonthlyCloseStatus;
import io.github.skr416.atria.attendance.repository.MonthlyCloseRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import io.github.skr416.atria.iam.security.AtriaUserPrincipal;
import io.github.skr416.atria.iam.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing monthly attendance settlement closing locks and checks for blocking pending requests
 * (ADR-0003 & ADR-0008 Decision 7).
 */
@Service
public class MonthlyCloseService {

    private final MonthlyCloseRepository monthlyCloseRepository;
    private final List<PendingRequestChecker> pendingRequestCheckers;

    public MonthlyCloseService(
            MonthlyCloseRepository monthlyCloseRepository,
            @Autowired(required = false) List<PendingRequestChecker> pendingRequestCheckers
    ) {
        this.monthlyCloseRepository = monthlyCloseRepository;
        this.pendingRequestCheckers = pendingRequestCheckers != null ? pendingRequestCheckers : List.of();
    }

    /**
     * Lists monthly close records, optionally filtered by calendar code.
     */
    public List<MonthlyCloseResponse> listMonthlyCloses(String calendarCode) {
        List<MonthlyClose> list;
        if (calendarCode != null && !calendarCode.isBlank()) {
            list = monthlyCloseRepository.findByCalendarCodeOrderByYearMonthDesc(calendarCode.trim().toUpperCase());
        } else {
            list = monthlyCloseRepository.findAllByOrderByYearMonthDescCalendarCodeAsc();
        }
        return list.stream().map(MonthlyCloseResponse::from).toList();
    }

    /**
     * Checks if a specific calendar region and year-month period is currently closed.
     */
    public boolean isMonthClosed(String calendarCode, YearMonth yearMonth) {
        if (yearMonth == null) {
            return false;
        }
        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";
        return monthlyCloseRepository.existsByCalendarCodeAndYearMonthAndStatus(
                code,
                yearMonth.toString(),
                MonthlyCloseStatus.CLOSED
        );
    }

    /**
     * Queries all registered {@link PendingRequestChecker} beans for unresolved requests in the target month.
     */
    public List<BlockingPendingRequestDto> checkBlockingRequests(String calendarCode, String yearMonthStr) {
        YearMonth ym;
        try {
            if (yearMonthStr == null || yearMonthStr.isBlank()) {
                throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
            }
            ym = YearMonth.parse(yearMonthStr.trim());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
        }

        String code = (calendarCode != null && !calendarCode.isBlank())
                ? calendarCode.trim().toUpperCase()
                : "TW";

        List<BlockingPendingRequestDto> blockingRequests = new ArrayList<>();
        for (PendingRequestChecker checker : pendingRequestCheckers) {
            List<BlockingPendingRequestDto> list = checker.findPendingRequestsForMonth(code, ym);
            if (list != null && !list.isEmpty()) {
                blockingRequests.addAll(list);
            }
        }
        return blockingRequests;
    }

    /**
     * Executes monthly closing lock on the specified calendar region and year-month.
     */
    @Transactional
    @AuditLog(action = "ATT_MONTHLY_CLOSE", targetType = "MONTHLY_CLOSE")
    public MonthlyCloseResponse closeMonth(MonthlyCloseRequest req) {
        if (req == null || req.yearMonth() == null || req.yearMonth().isBlank()) {
            throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(req.yearMonth().trim());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
        }

        String code = (req.calendarCode() != null && !req.calendarCode().isBlank())
                ? req.calendarCode().trim().toUpperCase()
                : "TW";
        String yearMonthStr = ym.toString();

        List<BlockingPendingRequestDto> blockingList = checkBlockingRequests(code, yearMonthStr);
        if (!blockingList.isEmpty()) {
            throw new BusinessException(ErrorCode.ATT_MONTHLY_CLOSE_HAS_PENDING_REQUESTS).withData(blockingList);
        }

        MonthlyClose close = monthlyCloseRepository.findByCalendarCodeAndYearMonth(code, yearMonthStr)
                .orElseGet(() -> {
                    MonthlyClose c = new MonthlyClose();
                    c.setCalendarCode(code);
                    c.setYearMonth(yearMonthStr);
                    return c;
                });

        if (close.getId() != null && close.getStatus() == MonthlyCloseStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ATT_MONTHLY_CLOSE_ALREADY_CLOSED);
        }

        AtriaUserPrincipal principal = SecurityUtils.currentPrincipal().orElse(null);
        close.setStatus(MonthlyCloseStatus.CLOSED);
        close.setClosedAt(Instant.now());
        if (principal != null) {
            close.setClosedBy(principal.getUserId());
            close.setClosedByUsername(principal.getUsername());
        }
        close.setNote(req.note());

        MonthlyClose saved = monthlyCloseRepository.save(close);
        return MonthlyCloseResponse.from(saved);
    }

    /**
     * Reopens a previously locked monthly period.
     */
    @Transactional
    @AuditLog(action = "ATT_MONTHLY_REOPEN", targetType = "MONTHLY_CLOSE")
    public MonthlyCloseResponse reopenMonth(MonthlyReopenRequest req) {
        if (req == null || req.yearMonth() == null || req.yearMonth().isBlank()) {
            throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(req.yearMonth().trim());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.ATT_INVALID_YEAR_MONTH);
        }

        String code = (req.calendarCode() != null && !req.calendarCode().isBlank())
                ? req.calendarCode().trim().toUpperCase()
                : "TW";
        String yearMonthStr = ym.toString();

        MonthlyClose close = monthlyCloseRepository.findByCalendarCodeAndYearMonth(code, yearMonthStr)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_MONTHLY_CLOSE_NOT_FOUND));

        AtriaUserPrincipal principal = SecurityUtils.currentPrincipal().orElse(null);
        close.setStatus(MonthlyCloseStatus.REOPENED);
        close.setReopenedAt(Instant.now());
        if (principal != null) {
            close.setReopenedBy(principal.getUserId());
        }
        if (req.note() != null) {
            close.setNote(req.note());
        }

        MonthlyClose saved = monthlyCloseRepository.save(close);
        return MonthlyCloseResponse.from(saved);
    }
}
