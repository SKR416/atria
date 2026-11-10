package io.github.skr416.atria.attendance.service;

import io.github.skr416.atria.attendance.dto.leave.LeaveBalanceBucketDto;
import io.github.skr416.atria.attendance.dto.leave.LeaveDurationResult;
import io.github.skr416.atria.attendance.dto.leave.ManualLeaveGrantRequest;
import io.github.skr416.atria.attendance.dto.request.AttendanceRequestResponse;
import io.github.skr416.atria.attendance.dto.request.LeaveRequestSubmitDto;
import io.github.skr416.atria.attendance.dto.request.OvertimeRequestSubmitDto;
import io.github.skr416.atria.attendance.dto.request.PunchCorrectionRequestSubmitDto;
import io.github.skr416.atria.attendance.event.AttRequestApprovedEvent;
import io.github.skr416.atria.attendance.event.AttRequestCancelledEvent;
import io.github.skr416.atria.attendance.event.AttRequestRejectedEvent;
import io.github.skr416.atria.attendance.event.AttRequestRevokedEvent;
import io.github.skr416.atria.attendance.event.AttRequestSubmittedEvent;
import io.github.skr416.atria.attendance.model.AttendanceRequest;
import io.github.skr416.atria.attendance.model.AttendanceRequestStatus;
import io.github.skr416.atria.attendance.model.AttendanceRequestType;
import io.github.skr416.atria.attendance.model.LeaveBalance;
import io.github.skr416.atria.attendance.model.LeaveType;
import io.github.skr416.atria.attendance.model.OvertimeCompensationType;
import io.github.skr416.atria.attendance.model.PunchLog;
import io.github.skr416.atria.attendance.model.PunchSource;
import io.github.skr416.atria.attendance.repository.AttendanceRequestRepository;
import io.github.skr416.atria.attendance.repository.CompLeaveGrantRepository;
import io.github.skr416.atria.attendance.repository.LeaveTypeRepository;
import io.github.skr416.atria.attendance.repository.PunchLogRepository;
import io.github.skr416.atria.common.api.ErrorCode;
import io.github.skr416.atria.common.exception.BusinessException;
import io.github.skr416.atria.iam.model.Department;
import io.github.skr416.atria.iam.model.User;
import io.github.skr416.atria.iam.repository.DepartmentRepository;
import io.github.skr416.atria.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified attendance request service orchestrating submission, withdrawal, and workflow approval lifecycle
 * for LEAVE, OVERTIME, and PUNCH_CORRECTION requests (ADR-0008 Decisions 3, 4, 5 & 9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRequestService {

    private final AttendanceRequestRepository attendanceRequestRepository;
    private final LeaveDurationCalculator leaveDurationCalculator;
    private final LeaveBalanceLedger leaveBalanceLedger;
    private final AttRequestGuard attRequestGuard;
    private final OvertimeRuleAndCalculatorService overtimeRuleAndCalculatorService;
    private final CompLeaveGrantService compLeaveGrantService;
    private final CompLeaveGrantRepository compLeaveGrantRepository;
    private final AttendanceRecordCalculator attendanceRecordCalculator;
    private final WorkScheduleResolver workScheduleResolver;
    private final PunchLogRepository punchLogRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Submits a new leave request, validates attachments, checks monthly closing & retroactive limits,
     * freezes FIFO leave balance, and publishes {@link AttRequestSubmittedEvent}.
     */
    @Transactional
    public AttendanceRequestResponse submitLeaveRequest(Long userId, LeaveRequestSubmitDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long deptId = user.getDepartmentId();
        String calendarCode = "TW";
        if (deptId != null) {
            calendarCode = departmentRepository.findById(deptId)
                    .map(Department::getCalendarCode)
                    .orElse("TW");
        }

        LeaveType leaveType = leaveTypeRepository.findByCode(dto.leaveTypeCode())
                .filter(LeaveType::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_TYPE_NOT_FOUND));

        if (leaveType.isAttachmentRequired() && (dto.attachmentPath() == null || dto.attachmentPath().isBlank())) {
            throw new BusinessException(ErrorCode.ATT_ATTACHMENT_REQUIRED);
        }

        LeaveDurationResult dur = leaveDurationCalculator.calculateDuration(
                userId,
                leaveType.getCode(),
                dto.startTime(),
                dto.endTime()
        );

        attRequestGuard.assertMonthOpen(calendarCode, dur.startDate(), dur.endDate());
        attRequestGuard.assertRetroactiveLimit(calendarCode, dur.startDate(), LocalDate.now());

        AttendanceRequest request = new AttendanceRequest();
        request.setUserId(userId);
        request.setDepartmentId(deptId);
        request.setCalendarCode(calendarCode);
        request.setRequestType(AttendanceRequestType.LEAVE);
        request.setStatus(AttendanceRequestStatus.PENDING);
        request.setLeaveTypeCode(leaveType.getCode());
        request.setStartDate(dur.startDate());
        request.setEndDate(dur.endDate());
        request.setStartTime(dto.startTime());
        request.setEndTime(dto.endTime());
        request.setRequestedMinutes(dur.roundedNetMinutes());
        request.setWeightedMinutes(dur.roundedNetMinutes());
        request.setReason(dto.reason());
        request.setAttachmentPath(dto.attachmentPath());

        AttendanceRequest saved = attendanceRequestRepository.save(request);

        leaveBalanceLedger.freeze(saved.getId(), userId, leaveType.getCode(), dur.roundedNetMinutes(), dur.startDate());

        eventPublisher.publishEvent(new AttRequestSubmittedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getDepartmentId(),
                saved.getCalendarCode(),
                saved.getRequestType(),
                saved.getLeaveTypeCode(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getRequestedMinutes(),
                saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now()
        ));

        return toResponse(saved);
    }

    /**
     * Submits a new overtime request, evaluates rate bands, validates regional overtime caps (WARN/BLOCK),
     * and publishes {@link AttRequestSubmittedEvent}.
     */
    @Transactional
    public AttendanceRequestResponse submitOvertimeRequest(Long userId, OvertimeRequestSubmitDto dto) {
        if (!dto.endTime().isAfter(dto.startTime())) {
            throw new BusinessException(ErrorCode.ATT_INVALID_OVERTIME_RANGE);
        }

        ResolvedWorkSchedule schedule = workScheduleResolver.resolve(userId, dto.workDate());
        String calendarCode = schedule.calendarCode();

        attRequestGuard.assertMonthOpen(calendarCode, dto.workDate(), dto.workDate());
        attRequestGuard.assertRetroactiveLimit(calendarCode, dto.workDate(), LocalDate.now());

        int rawMins = (int) Duration.between(dto.startTime(), dto.endTime()).toMinutes();
        if (rawMins <= 0) {
            throw new BusinessException(ErrorCode.ATT_INVALID_OVERTIME_RANGE);
        }

        var calc = overtimeRuleAndCalculatorService.calculateOvertime(
                calendarCode,
                schedule.dayType(),
                rawMins,
                dto.compLeaveMode()
        );

        List<String> warnings = overtimeRuleAndCalculatorService.checkOvertimeCaps(
                userId,
                calendarCode,
                dto.workDate(),
                rawMins
        );

        AttendanceRequest request = new AttendanceRequest();
        request.setUserId(userId);
        request.setDepartmentId(schedule.departmentId());
        request.setCalendarCode(calendarCode);
        request.setRequestType(AttendanceRequestType.OVERTIME);
        request.setStatus(AttendanceRequestStatus.PENDING);
        request.setStartDate(dto.workDate());
        request.setEndDate(dto.workDate());
        request.setStartTime(dto.startTime());
        request.setEndTime(dto.endTime());
        request.setRequestedMinutes(rawMins);
        request.setWeightedMinutes(calc.totalWeightedMinutes());
        request.setOvertimeCompType(dto.overtimeCompType());
        request.setCompLeaveMode(calc.effectiveCompLeaveMode());
        request.setReason(dto.reason());
        request.setWarningMessages(warnings.isEmpty() ? null : String.join("; ", warnings));

        AttendanceRequest saved = attendanceRequestRepository.save(request);

        eventPublisher.publishEvent(new AttRequestSubmittedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getDepartmentId(),
                saved.getCalendarCode(),
                saved.getRequestType(),
                null,
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getRequestedMinutes(),
                saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now()
        ));

        return toResponse(saved);
    }

    /**
     * Submits a new punch correction request and publishes {@link AttRequestSubmittedEvent}.
     */
    @Transactional
    public AttendanceRequestResponse submitPunchCorrectionRequest(Long userId, PunchCorrectionRequestSubmitDto dto) {
        ResolvedWorkSchedule schedule = workScheduleResolver.resolve(userId, dto.workDate());
        String calendarCode = schedule.calendarCode();

        attRequestGuard.assertMonthOpen(calendarCode, dto.workDate(), dto.workDate());
        attRequestGuard.assertRetroactiveLimit(calendarCode, dto.workDate(), LocalDate.now());

        AttendanceRequest request = new AttendanceRequest();
        request.setUserId(userId);
        request.setDepartmentId(schedule.departmentId());
        request.setCalendarCode(calendarCode);
        request.setRequestType(AttendanceRequestType.PUNCH_CORRECTION);
        request.setStatus(AttendanceRequestStatus.PENDING);
        request.setStartDate(dto.workDate());
        request.setEndDate(dto.workDate());
        request.setStartTime(dto.correctedPunchTime());
        request.setEndTime(dto.correctedPunchTime());
        request.setRequestedMinutes(0);
        request.setWeightedMinutes(0);
        request.setReason(dto.reason());
        request.setAttachmentPath(dto.attachmentPath());

        AttendanceRequest saved = attendanceRequestRepository.save(request);

        eventPublisher.publishEvent(new AttRequestSubmittedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getDepartmentId(),
                saved.getCalendarCode(),
                saved.getRequestType(),
                null,
                saved.getStartDate(),
                saved.getEndDate(),
                0,
                saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now()
        ));

        return toResponse(saved);
    }

    /**
     * Cancels an unresolved PENDING request, unfreezing frozen balance if LEAVE, and publishes {@link AttRequestCancelledEvent}.
     */
    @Transactional
    public AttendanceRequestResponse cancelRequest(Long requestId, Long actorUserId) {
        AttendanceRequest request = attendanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND));

        if (!java.util.Objects.equals(request.getUserId(), actorUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (request.getStatus() != AttendanceRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ATT_REQUEST_INVALID_STATE_TRANSITION);
        }

        attRequestGuard.assertMonthOpen(request.getCalendarCode(), request.getStartDate(), request.getEndDate());

        if (request.getRequestType() == AttendanceRequestType.LEAVE) {
            leaveBalanceLedger.restoreFromPending(request.getId());
        }

        request.setStatus(AttendanceRequestStatus.CANCELLED);
        AttendanceRequest saved = attendanceRequestRepository.save(request);

        eventPublisher.publishEvent(new AttRequestCancelledEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getRequestType(),
                Instant.now()
        ));

        return toResponse(saved);
    }

    /**
     * Handles approval events: commits leave balances, converts overtime to compensatory leave,
     * writes immutable correction punch log, and recomputes daily attendance records.
     */
    @EventListener
    @Transactional
    public void onRequestApproved(AttRequestApprovedEvent event) {
        AttendanceRequest request = attendanceRequestRepository.findById(event.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND));

        if (request.getStatus() != AttendanceRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ATT_REQUEST_INVALID_STATE_TRANSITION);
        }

        attRequestGuard.assertMonthOpen(request.getCalendarCode(), request.getStartDate(), request.getEndDate());

        request.setStatus(AttendanceRequestStatus.APPROVED);
        request.setReviewedBy(event.reviewerId());
        request.setReviewedAt(event.approvedAt() != null ? event.approvedAt() : Instant.now());
        request.setReviewComment(event.comment());
        attendanceRequestRepository.save(request);

        if (request.getRequestType() == AttendanceRequestType.LEAVE) {
            leaveBalanceLedger.commit(request.getId());
        } else if (request.getRequestType() == AttendanceRequestType.OVERTIME) {
            if (request.getOvertimeCompType() == OvertimeCompensationType.COMP_LEAVE) {
                ResolvedWorkSchedule sched = workScheduleResolver.resolve(request.getUserId(), request.getStartDate());
                compLeaveGrantService.grantFromApprovedOvertime(request, sched.dayType());
            }
        } else if (request.getRequestType() == AttendanceRequestType.PUNCH_CORRECTION) {
            PunchLog log = new PunchLog();
            log.setUserId(request.getUserId());
            log.setDepartmentId(request.getDepartmentId());
            log.setWorkDate(request.getStartDate());
            log.setRawPunchTime(request.getStartTime());
            log.setClientIp("127.0.0.1");
            log.setSource(PunchSource.CORRECTION);
            log.setCorrectionRequestId(request.getId());
            log.setNote("Approved punch correction #" + request.getId());
            punchLogRepository.saveAndFlush(log);
        }

        for (LocalDate d = request.getStartDate(); !d.isAfter(request.getEndDate()); d = d.plusDays(1)) {
            attendanceRecordCalculator.recomputeDailyRecord(request.getUserId(), d, false);
        }
    }

    /**
     * Handles rejection events: releases frozen leave balances.
     */
    @EventListener
    @Transactional
    public void onRequestRejected(AttRequestRejectedEvent event) {
        AttendanceRequest request = attendanceRequestRepository.findById(event.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND));

        if (request.getStatus() != AttendanceRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.ATT_REQUEST_INVALID_STATE_TRANSITION);
        }

        attRequestGuard.assertMonthOpen(request.getCalendarCode(), request.getStartDate(), request.getEndDate());

        if (request.getRequestType() == AttendanceRequestType.LEAVE) {
            leaveBalanceLedger.restoreFromPending(request.getId());
        }

        request.setStatus(AttendanceRequestStatus.REJECTED);
        request.setReviewedBy(event.reviewerId());
        request.setReviewedAt(event.rejectedAt() != null ? event.rejectedAt() : Instant.now());
        request.setReviewComment(event.comment());
        attendanceRequestRepository.save(request);
    }

    /**
     * Handles post-approval revocation events: checks month locks & comp-leave usage guards,
     * restores committed leave balance or soft-deletes comp-grant bucket, and recomputes daily records.
     */
    @EventListener
    @Transactional
    public void onRequestRevoked(AttRequestRevokedEvent event) {
        AttendanceRequest request = attendanceRequestRepository.findById(event.requestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND));

        if (request.getStatus() != AttendanceRequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ATT_REQUEST_INVALID_STATE_TRANSITION);
        }

        attRequestGuard.assertCanRevokeApprovedRequest(request);

        if (request.getRequestType() == AttendanceRequestType.LEAVE) {
            leaveBalanceLedger.restoreFromUsed(request.getId());
        } else if (request.getRequestType() == AttendanceRequestType.OVERTIME
                && request.getOvertimeCompType() == OvertimeCompensationType.COMP_LEAVE) {
            compLeaveGrantRepository.findBySourceRequestId(request.getId()).ifPresent(grant -> {
                leaveBalanceLedger.revokeCompGrantBucket(grant.getId());
                grant.setDeleted(true);
                compLeaveGrantRepository.save(grant);
            });
        }

        request.setStatus(AttendanceRequestStatus.REVOKED);
        request.setRevokedBy(event.actorId());
        request.setRevokedAt(event.revokedAt() != null ? event.revokedAt() : Instant.now());
        request.setRevokeReason(event.reason());
        attendanceRequestRepository.save(request);

        for (LocalDate d = request.getStartDate(); !d.isAfter(request.getEndDate()); d = d.plusDays(1)) {
            attendanceRecordCalculator.recomputeDailyRecord(request.getUserId(), d, false);
        }
    }

    public List<AttendanceRequestResponse> listMyRequests(Long userId) {
        return attendanceRequestRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public List<AttendanceRequestResponse> listAllRequests(AttendanceRequestStatus status) {
        List<AttendanceRequest> list = (status != null)
                ? attendanceRequestRepository.findByStatusOrderByCreatedAtDescIdDesc(status)
                : attendanceRequestRepository.findAllByOrderByCreatedAtDescIdDesc();
        return list.stream().map(this::toResponse).toList();
    }

    public AttendanceRequestResponse getRequestResponse(Long id) {
        return toResponse(attendanceRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_REQUEST_NOT_FOUND)));
    }

    /**
     * Maps an {@link AttendanceRequest} entity to trilingual DTO response.
     */
    public AttendanceRequestResponse toResponse(AttendanceRequest r) {
        User u = userRepository.findById(r.getUserId()).orElse(null);
        String username = u != null ? u.getUsername() : "user#" + r.getUserId();
        String displayName = u != null ? u.getDisplayName() : username;

        String localizedLeaveTypeName = null;
        if (r.getLeaveTypeCode() != null) {
            localizedLeaveTypeName = leaveTypeRepository.findByCode(r.getLeaveTypeCode())
                    .map(lt -> {
                        Locale locale = LocaleContextHolder.getLocale();
                        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
                        Map<String, String> map = lt.getName() != null ? lt.getName() : Map.of();
                        return map.getOrDefault(langTag, map.getOrDefault("zh-TW", map.getOrDefault("en-US", lt.getCode())));
                    })
                    .orElse(r.getLeaveTypeCode());
        }

        return new AttendanceRequestResponse(
                r.getId(),
                r.getUserId(),
                username,
                displayName,
                r.getDepartmentId(),
                r.getCalendarCode(),
                r.getRequestType(),
                r.getStatus(),
                r.getLeaveTypeCode(),
                localizedLeaveTypeName,
                r.getStartDate(),
                r.getEndDate(),
                r.getStartTime(),
                r.getEndTime(),
                r.getRequestedMinutes(),
                r.getWeightedMinutes(),
                r.getOvertimeCompType(),
                r.getCompLeaveMode(),
                r.getReason(),
                r.getAttachmentPath(),
                r.getWarningMessages(),
                r.getReviewedBy(),
                r.getReviewedAt(),
                r.getReviewComment(),
                r.getRevokedBy(),
                r.getRevokedAt(),
                r.getRevokeReason(),
                r.getCreatedAt()
        );
    }

    /**
     * Creates a manual HR leave balance bucket directly via {@link LeaveBalanceLedger}.
     */
    @Transactional
    @io.github.skr416.atria.iam.audit.AuditLog(action = "ATT_LEAVE_MANUAL_GRANT", targetType = "LEAVE_BALANCE")
    public LeaveBalanceBucketDto manualGrant(ManualLeaveGrantRequest req) {
        if (req.validUntil().isBefore(req.validFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String calendarCode = "TW";
        if (user.getDepartmentId() != null) {
            calendarCode = departmentRepository.findById(user.getDepartmentId())
                    .map(Department::getCalendarCode)
                    .orElse("TW");
        }
        LeaveType lt = leaveTypeRepository.findByCode(req.leaveTypeCode())
                .filter(LeaveType::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_LEAVE_TYPE_NOT_FOUND));

        LeaveBalance bucket = leaveBalanceLedger.createBucket(
                req.userId(),
                req.leaveTypeCode(),
                calendarCode,
                req.validFrom().getYear(),
                req.grantMinutes(),
                req.validFrom(),
                req.validUntil(),
                null,
                0,
                req.note()
        );

        Locale locale = LocaleContextHolder.getLocale();
        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
        Map<String, String> map = lt.getName() != null ? lt.getName() : Map.of();
        String localizedName = map.getOrDefault(langTag, map.getOrDefault("zh-TW", map.getOrDefault("en-US", lt.getCode())));
        return LeaveBalanceBucketDto.from(bucket, localizedName);
    }
}
