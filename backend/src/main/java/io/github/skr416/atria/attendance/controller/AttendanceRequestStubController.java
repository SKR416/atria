package io.github.skr416.atria.attendance.controller;

import io.github.skr416.atria.attendance.dto.request.AttendanceRequestDecisionDto;
import io.github.skr416.atria.attendance.dto.request.AttendanceRequestResponse;
import io.github.skr416.atria.attendance.dto.request.AttendanceRequestRevokeDto;
import io.github.skr416.atria.attendance.event.AttRequestApprovedEvent;
import io.github.skr416.atria.attendance.event.AttRequestRejectedEvent;
import io.github.skr416.atria.attendance.event.AttRequestRevokedEvent;
import io.github.skr416.atria.attendance.service.AttendanceRequestService;
import io.github.skr416.atria.common.api.ApiResponse;
import io.github.skr416.atria.iam.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Dev/test stub controller allowing direct approval, rejection, and revocation of attendance requests
 * in non-production profiles without depending on Phase 4 workflow engine (ADR-0008 Decision 9).
 */
@RestController
@RequestMapping("/api/v1/attendance/requests")
@Profile({"local", "pgtest"})
@RequiredArgsConstructor
@Tag(name = "Attendance - Requests Dev Stub", description = "Development/testing stub endpoints for direct approval/rejection/revocation of attendance requests")
public class AttendanceRequestStubController {

    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceRequestService attendanceRequestService;

    @Operation(summary = "Stub-approve a pending attendance request (dev/test only)")
    @PostMapping("/{id}/stub-approve")
    public ApiResponse<AttendanceRequestResponse> stubApprove(
            @PathVariable Long id,
            @RequestBody(required = false) AttendanceRequestDecisionDto dto
    ) {
        Long reviewerId = SecurityUtils.currentUserIdOrNull();
        if (reviewerId == null) {
            reviewerId = 1L;
        }
        String comment = (dto != null && dto.comment() != null) ? dto.comment() : "Approved via dev stub";
        eventPublisher.publishEvent(new AttRequestApprovedEvent(id, reviewerId, comment, Instant.now()));
        return ApiResponse.ok(attendanceRequestService.getRequestResponse(id));
    }

    @Operation(summary = "Stub-reject a pending attendance request (dev/test only)")
    @PostMapping("/{id}/stub-reject")
    public ApiResponse<AttendanceRequestResponse> stubReject(
            @PathVariable Long id,
            @RequestBody(required = false) AttendanceRequestDecisionDto dto
    ) {
        Long reviewerId = SecurityUtils.currentUserIdOrNull();
        if (reviewerId == null) {
            reviewerId = 1L;
        }
        String comment = (dto != null && dto.comment() != null) ? dto.comment() : "Rejected via dev stub";
        eventPublisher.publishEvent(new AttRequestRejectedEvent(id, reviewerId, comment, Instant.now()));
        return ApiResponse.ok(attendanceRequestService.getRequestResponse(id));
    }

    @Operation(summary = "Stub-revoke an approved attendance request (dev/test only)")
    @PostMapping("/{id}/stub-revoke")
    public ApiResponse<AttendanceRequestResponse> stubRevoke(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRequestRevokeDto dto
    ) {
        Long actorId = SecurityUtils.currentUserIdOrNull();
        if (actorId == null) {
            actorId = 1L;
        }
        eventPublisher.publishEvent(new AttRequestRevokedEvent(id, actorId, dto.reason(), Instant.now()));
        return ApiResponse.ok(attendanceRequestService.getRequestResponse(id));
    }
}
