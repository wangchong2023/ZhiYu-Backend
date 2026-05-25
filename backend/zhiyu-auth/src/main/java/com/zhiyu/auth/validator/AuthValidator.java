package com.zhiyu.auth.validator;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    private static final int USERNAME_MIN = 4;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    public void validateUsername(final String username) {
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new BizException(BizErrorCode.VALIDATION_USERNAME_LENGTH);
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(BizErrorCode.VALIDATION_USERNAME_FORMAT);
        }
    }

    public void validatePassword(final String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BizException(BizErrorCode.VALIDATION_PASSWORD_LENGTH);
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new BizException(BizErrorCode.VALIDATION_PASSWORD_FORMAT);
        }
    }

    public void validateEmail(final String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new BizException(BizErrorCode.VALIDATION_EMAIL_FORMAT);
        }
    }
}
