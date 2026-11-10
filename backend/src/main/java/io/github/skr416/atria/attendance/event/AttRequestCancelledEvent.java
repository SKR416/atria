package io.github.skr416.atria.attendance.event;

import io.github.skr416.atria.attendance.model.AttendanceRequestType;

import java.time.Instant;

/**
 * Domain event published by {@code attendance} when an applicant withdraws a {@code PENDING} request
 * ({@code PENDING -> CANCELLED}) (ADR-0008 Decision 9).
 */
public record AttRequestCancelledEvent(
        Long requestId,
        Long userId,
        AttendanceRequestType requestType,
        Instant cancelledAt
) {
}
