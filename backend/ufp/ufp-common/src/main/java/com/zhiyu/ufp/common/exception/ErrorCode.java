package com.zhiyu.ufp.common.exception;

public interface ErrorCode {
    int getCode();
    String getMessage();

    default String getI18nKey() {
        return null;
    }
}
