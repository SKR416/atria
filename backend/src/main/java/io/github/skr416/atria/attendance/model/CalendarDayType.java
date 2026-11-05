package io.github.skr416.atria.attendance.model;

/**
 * Legal calendar day types aligned with Taiwan LSA and Japan LSA (ADR-0008 Decision 2).
 */
public enum CalendarDayType {
    /** Standard working day (平日 / 所定労働日) */
    WORKDAY,
    /** Adjusted makeup working day (補班日) */
    MAKEUP_WORKDAY,
    /** Scheduled rest day, e.g. Saturday (休息日 / 所定休日) */
    REST_DAY,
    /** Statutory rest day, e.g. Sunday (例假日 / 法定休日) */
    STATUTORY_REST_DAY,
    /** National / public holiday (國定假日 / 国民の祝日) */
    NATIONAL_HOLIDAY;

    public boolean isWorkingDay() {
        return this == WORKDAY || this == MAKEUP_WORKDAY;
    }
}
