package com.zhiyu.auth.validator;

import com.zhiyu.ufp.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    private static final int ERR_VALIDATION = 40001;
    private static final int USERNAME_MIN = 4;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    public void validateUsername(final String username) {
        if (username == null || username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw new BizException(ERR_VALIDATION, "Username must be 4-32 characters");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(ERR_VALIDATION, "Username may only contain letters, digits, and underscores");
        }
    }

    public void validatePassword(final String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BizException(ERR_VALIDATION, "Password must be 8-128 characters");
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new BizException(ERR_VALIDATION, "Password must contain uppercase, lowercase, and digits");
        }
    }

    public void validateEmail(final String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new BizException(ERR_VALIDATION, "Invalid email format");
        }
    }
}
