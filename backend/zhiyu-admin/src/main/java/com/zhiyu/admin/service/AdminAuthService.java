package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthUserMapper authUserMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(request.getPassword(), user.getAuthUserPassword())) {
            throw new BizException(40105, "用户名或密码错误");
        }
        if (!"ADMIN".equals(user.getAuthUserScope())) {
            throw new BizException(40301, "无管理员权限");
        }
        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(40107, "账号已被禁用");
        }

        var pair = jwtService.issue(user.getAuthUserId(), user.getAuthUserUsername(), "admin");
        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }
}
