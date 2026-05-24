package com.zhiyu.ufp.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final String message;
    private final ErrorCode errorCode;

    public BizException(final int code, final String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorCode = null;
    }

    public BizException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.errorCode = errorCode;
    }
}
