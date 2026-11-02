package io.github.skr416.atria.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Strongly-typed business error codes mapped to i18n message keys (ADR-0002).
 * Never hardcode user-facing error strings in exceptions; always reference an ErrorCode.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Common & System (200 / 4xx / 5xx) ---
    SUCCESS(200, HttpStatus.OK, "common.success"),
    BAD_REQUEST(400, HttpStatus.BAD_REQUEST, "error.bad_request"),
    VALIDATION_FAILED(400, HttpStatus.BAD_REQUEST, "error.validation"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "error.unauthorized"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "error.forbidden"),
    NOT_FOUND(404, HttpStatus.NOT_FOUND, "error.not_found"),
    CONFLICT(409, HttpStatus.CONFLICT, "error.conflict"),
    SYSTEM_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "error.system"),

    // --- IAM & Security (1xxx) ---
    AUTH_INVALID_CREDENTIALS(1001, HttpStatus.UNAUTHORIZED, "error.iam.invalid_credentials"),
    AUTH_ACCOUNT_LOCKED(1002, HttpStatus.LOCKED, "error.iam.account_locked"),
    AUTH_PASSWORD_CHANGE_REQUIRED(1003, HttpStatus.FORBIDDEN, "error.iam.password_change_required"),
    USER_NOT_FOUND(1004, HttpStatus.NOT_FOUND, "error.iam.user_not_found"),
    DEPT_NOT_FOUND(1005, HttpStatus.NOT_FOUND, "error.iam.dept_not_found"),
    BOT_SIGNATURE_INVALID(1006, HttpStatus.UNAUTHORIZED, "error.iam.bot_signature_invalid"),

    // --- Attendance (2xxx) ---
    ATT_PUNCH_DUPLICATE(2001, HttpStatus.CONFLICT, "error.att.punch_duplicate"),
    ATT_INVALID_LEAVE_RANGE(2002, HttpStatus.BAD_REQUEST, "error.att.invalid_leave_range"),
    ATT_ATTACHMENT_REQUIRED(2003, HttpStatus.BAD_REQUEST, "error.att.attachment_required"),
    ATT_INSUFFICIENT_LEAVE_BALANCE(2004, HttpStatus.BAD_REQUEST, "error.att.insufficient_leave_balance"),
    ATT_MONTHLY_PERIOD_CLOSED(2005, HttpStatus.FORBIDDEN, "error.att.monthly_period_closed"),
    ATT_RETROACTIVE_LIMIT_EXCEEDED(2006, HttpStatus.BAD_REQUEST, "error.att.retroactive_limit_exceeded"),

    // --- Workflow (3xxx) ---
    WF_INSTANCE_NOT_FOUND(3001, HttpStatus.NOT_FOUND, "error.wf.instance_not_found"),
    WF_CANNOT_REVOKE_APPROVED(3002, HttpStatus.BAD_REQUEST, "error.wf.cannot_revoke_approved"),
    WF_UNAUTHORIZED_APPROVER(3003, HttpStatus.FORBIDDEN, "error.wf.unauthorized_approver");

    private final int code;
    private final HttpStatus httpStatus;
    private final String messageKey;
}
