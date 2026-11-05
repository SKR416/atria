package io.github.skr416.atria.attendance.model;

/**
 * Origin channel of an immutable punch event in {@code att_punch_log} (ADR-0008 Decision 1).
 */
public enum PunchSource {
    /** Employee self-service clock-in/out from web portal */
    WEB,
    /** Authorized third-party bot proxy (Slack / Teams / LINE) verified via HMAC-SHA256 */
    BOT,
    /** Approved retroactive punch correction workflow request */
    CORRECTION
}
