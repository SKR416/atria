package io.github.skr416.atria.notification.dto;

import java.time.LocalDate;

/**
 * Request payload for manually triggering the monthly leave balance digest batch job (ADR-0008 Decision 8).
 */
public record DigestTriggerRequest(
        String periodYearMonth,
        LocalDate asOfDate
) {
}
