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
            throw new BizException(ERR_VALIDATION, "用户名需 4-32 位");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(ERR_VALIDATION, "用户名仅支持字母、数字、下划线");
        }
    }

    public void validatePassword(final String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BizException(ERR_VALIDATION, "密码需 8-128 位");
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new BizException(ERR_VALIDATION, "密码需包含大小写字母和数字");
        }
    }

    public void validateEmail(final String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new BizException(ERR_VALIDATION, "邮箱格式不合法");
        }
    }
}
