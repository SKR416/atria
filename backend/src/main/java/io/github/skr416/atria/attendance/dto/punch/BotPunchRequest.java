package io.github.skr416.atria.attendance.dto.punch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for authorized third-party Bot proxy punch endpoint (ADR-0003).
 * Authenticated via {@code X-Atria-Bot-Timestamp} and {@code X-Atria-Bot-Signature} HMAC-SHA256 headers.
 */
public record BotPunchRequest(
        @NotBlank(message = "Employee username is required")
        String username,

        @NotBlank(message = "Bot client ID is required")
        @Size(max = 64)
        String botClientId,

        /** Optional explicit punch timestamp from bot; defaults to server UTC now if null */
        Instant punchTime,

        @Size(max = 255)
        String note
) {
}
