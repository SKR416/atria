package io.github.skr416.atria.common.exception;

import io.github.skr416.atria.common.api.ErrorCode;
import lombok.Getter;

/**
 * Domain exception carrying a typed {@link ErrorCode} and optional message interpolation arguments.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args != null ? args : new Object[0];
    }
}
