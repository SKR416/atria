package io.github.skr416.atria.attendance.dto.punch;

import jakarta.validation.constraints.Size;

/**
 * Request payload for employee self-service web clock-in / clock-out.
 */
public record WebPunchRequest(
        @Size(max = 255)
        String note
) {
}
