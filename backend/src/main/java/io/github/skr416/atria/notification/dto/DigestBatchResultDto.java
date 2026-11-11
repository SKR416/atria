package io.github.skr416.atria.notification.dto;

/**
 * Result DTO summarizing monthly leave digest batch delivery outcome (ADR-0008 Decision 8).
 */
public record DigestBatchResultDto(
        String periodYearMonth,
        int totalActiveUsers,
        int sentCount,
        int skippedCount
) {
}
