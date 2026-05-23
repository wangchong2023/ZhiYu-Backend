package com.zhiyu.auth.validator;

import com.zhiyu.ufp.common.exception.BizException;
import org.springframework.stereotype.Component;

@Component
public class AuthValidator {

    public void validateUsername(String username) {
        if (username == null || username.length() < 4 || username.length() > 32) {
            throw new BizException(40001, "用户名需 4-32 位");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BizException(40001, "用户名仅支持字母、数字、下划线");
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BizException(40001, "密码需 8-128 位");
        }
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")) {
            throw new BizException(40001, "密码需包含大小写字母和数字");
        }
    }

    public void validateEmail(String email) {
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new BizException(40001, "邮箱格式不合法");
        }
    }
}
