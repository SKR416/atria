package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.LeaveGrantRuleResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveGrantRuleUpsertRequest;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeResponse;
import io.github.skr416.atria.attendance.dto.leave.LeaveTypeUpsertRequest;
import io.github.skr416.atria.attendance.model.LeaveGrantRule;
import io.github.skr416.atria.attendance.model.LeaveType;
import io.github.skr416.atria.attendance.repository.LeaveGrantRuleRepository;
import io.github.skr416.atria.attendance.repository.LeaveTypeRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Service managing leave type catalog entries and statutory seniority grant tier rules (ADR-0008 Decision 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveGrantRuleRepository leaveGrantRuleRepository;

    /**
     * Lists leave types, optionally filtered to enabled-only, localized to the current request locale.
     */
    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> listLeaveTypes(boolean enabledOnly) {
        Locale locale = LocaleContextHolder.getLocale();
        List<LeaveType> list = enabledOnly
                ? leaveTypeRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
                : leaveTypeRepository.findAllByOrderBySortOrderAscIdAsc();
        return list.stream()
                .map(entity -> LeaveTypeResponse.from(entity, locale))
                .toList();
    }

    /**
     * Creates a new leave type definition. Throws ATT_LEAVE_TYPE_CODE_EXISTS (2015) if code is duplicated.
     */
    @Transactional
    @AuditLog(action = "ATT_LEAVE_TYPE_CREATE", targetType = "LEAVE_TYPE")
    public LeaveTypeResponse createLeaveType(LeaveTypeUpsertRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String code = req.code().trim().toUpperCase();
        if (leaveTypeRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.ATT_LEAVE_TYPE_CODE_EXISTS);
        }

        LeaveType entity = new LeaveType();
        entity.setCode(code);
        entity.setName(req.name());
        entity.setMinUnitMinutes(req.minUnitMinutes());
        entity.setPaid(req.paid());
        entity.setBalanceTracked(req.balanceTracked());
        entity.setAttachmentRequired(req.attachmentRequired());
        entity.setEnabled(req.enabled());
        entity.setSortOrder(req.sortOrder());
        entity.setDescription(req.description());

        LeaveType saved = leaveTypeRepository.save(entity);
        return LeaveTypeResponse.from(saved, LocaleContextHolder.getLocale());
    }

    /**
     * Updates an existing leave type definition by ID.
     */
    @Transactional
    @AuditLog(action = "ATT_LEAVE_TYPE_UPDATE", targetType = "LEAVE_TYPE")
    public LeaveTypeResponse updateLeaveType(Long id, LeaveTypeUpsertRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LeaveType entity = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_TYPE_NOT_FOUND));

        entity.setName(req.name());
        entity.setMinUnitMinutes(req.minUnitMinutes());
        entity.setPaid(req.paid());
        entity.setBalanceTracked(req.balanceTracked());
        entity.setAttachmentRequired(req.attachmentRequired());
        entity.setEnabled(req.enabled());
        entity.setSortOrder(req.sortOrder());
        entity.setDescription(req.description());

        LeaveType saved = leaveTypeRepository.save(entity);
        return LeaveTypeResponse.from(saved, LocaleContextHolder.getLocale());
    }

    /**
     * Lists seniority grant rules, optionally filtered by calendar code.
     */
    @Transactional(readOnly = true)
    public List<LeaveGrantRuleResponse> listGrantRules(String calendarCode) {
        List<LeaveGrantRule> rules;
        if (calendarCode != null && !calendarCode.isBlank()) {
            rules = leaveGrantRuleRepository.findByCalendarCodeOrderByMinTenureMonthsAsc(calendarCode.trim().toUpperCase());
        } else {
            rules = leaveGrantRuleRepository.findAllByOrderByCalendarCodeAscMinTenureMonthsAsc();
        }
        return rules.stream()
                .map(LeaveGrantRuleResponse::from)
                .toList();
    }

    /**
     * Creates a new seniority annual leave grant tier rule.
     */
    @Transactional
    @AuditLog(action = "ATT_LEAVE_GRANT_RULE_CREATE", targetType = "LEAVE_GRANT_RULE")
    public LeaveGrantRuleResponse createGrantRule(LeaveGrantRuleUpsertRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LeaveGrantRule rule = new LeaveGrantRule();
        rule.setCalendarCode(req.calendarCode().trim().toUpperCase());
        rule.setLeaveTypeCode(req.leaveTypeCode().trim().toUpperCase());
        rule.setGrantBasis(req.grantBasis());
        rule.setMinTenureMonths(req.minTenureMonths());
        rule.setMaxTenureMonths(req.maxTenureMonths());
        rule.setGrantDays(req.grantDays());
        rule.setGrantMinutes(req.grantDays() * 480);
        rule.setValidMonths(req.validMonths());
        rule.setNote(req.note());

        LeaveGrantRule saved = leaveGrantRuleRepository.save(rule);
        return LeaveGrantRuleResponse.from(saved);
    }

    /**
     * Updates an existing seniority annual leave grant tier rule by ID.
     */
    @Transactional
    @AuditLog(action = "ATT_LEAVE_GRANT_RULE_UPDATE", targetType = "LEAVE_GRANT_RULE")
    public LeaveGrantRuleResponse updateGrantRule(Long id, LeaveGrantRuleUpsertRequest req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LeaveGrantRule rule = leaveGrantRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_GRANT_RULE_NOT_FOUND));

        rule.setCalendarCode(req.calendarCode().trim().toUpperCase());
        rule.setLeaveTypeCode(req.leaveTypeCode().trim().toUpperCase());
        rule.setGrantBasis(req.grantBasis());
        rule.setMinTenureMonths(req.minTenureMonths());
        rule.setMaxTenureMonths(req.maxTenureMonths());
        rule.setGrantDays(req.grantDays());
        rule.setGrantMinutes(req.grantDays() * 480);
        rule.setValidMonths(req.validMonths());
        rule.setNote(req.note());

        LeaveGrantRule saved = leaveGrantRuleRepository.save(rule);
        return LeaveGrantRuleResponse.from(saved);
    }
}
