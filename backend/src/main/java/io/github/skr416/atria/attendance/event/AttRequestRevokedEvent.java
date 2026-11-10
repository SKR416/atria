package io.github.skr416.atria.attendance.event;

import java.time.Instant;

/**
 * Domain event consumed by {@code attendance} when a previously approved attendance request is revoked
 * ({@code APPROVED -> REVOKED}) (ADR-0008 Decision 9).
 */
public record AttRequestRevokedEvent(
        Long requestId,
        Long actorId,
        String reason,
        Instant revokedAt
) {
}
