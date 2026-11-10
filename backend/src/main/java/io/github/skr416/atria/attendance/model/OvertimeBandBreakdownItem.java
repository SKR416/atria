package io.github.skr416.atria.attendance.model;

/**
 * JSON-serializable breakdown of original overtime minutes per rate band stored in
 * {@code att_comp_leave_grant.band_breakdown_json} (ADR-0008 Decision 6).
 */
public record OvertimeBandBreakdownItem(
        int fromMinute,
        int toMinute,
        int minutes,
        String multiplier,
        int weightedMinutes
) {
}
