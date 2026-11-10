package io.github.skr416.atria.attendance.event;

import java.time.Instant;

/**
 * Domain event consumed by {@code attendance} when an attendance request is finally approved
 * ({@code PENDING -> APPROVED}) by the workflow engine or the dev/test approval stub (ADR-0008 Decision 9).
 */
public record AttRequestApprovedEvent(
        Long requestId,
        Long reviewerId,
        String comment,
        Instant approvedAt
) {
}
