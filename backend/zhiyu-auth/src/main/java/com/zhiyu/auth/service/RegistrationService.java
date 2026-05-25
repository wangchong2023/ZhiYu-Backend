package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final IAuthUserService authUserService;
    private final PasswordService passwordService;
    private final CaptchaService captchaService;
    private final AuthValidator authValidator;

    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(final RegisterRequest request) {
        authValidator.validateUsername(request.getUsername());
        authValidator.validatePassword(request.getPassword());
        authValidator.validateEmail(request.getEmail());
        captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());

        if (authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername())) != null) {
            throw new BizException(BizErrorCode.USERNAME_TAKEN);
        }
        if (authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMail, request.getEmail())) != null) {
            throw new BizException(BizErrorCode.EMAIL_TAKEN);
        }

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);
        user.setAuthUserPassword(passwordService.hash(request.getPassword()));
        authUserService.insert(user);

        return RegisterResponse.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .build();
    }
}