package io.github.skr416.atria.attendance.event;

import io.github.skr416.atria.attendance.model.AttendanceRequestType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain event published by {@code attendance} when an employee submits a new attendance request
 * ({@code LEAVE}, {@code OVERTIME}, or {@code PUNCH_CORRECTION}) (ADR-0008 Decision 9).
 * Phase 4 {@code workflow} listens to this event to instantiate an approval flow without cyclic dependency.
 */
public record AttRequestSubmittedEvent(
        Long requestId,
        Long userId,
        Long departmentId,
        String calendarCode,
        AttendanceRequestType requestType,
        String leaveTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        int requestedMinutes,
        Instant submittedAt
) {
}
