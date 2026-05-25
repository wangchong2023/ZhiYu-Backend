package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.mapper.AuthUserWebAuthnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthWebAuthnService {

    private final AuthUserWebAuthnMapper webAuthnMapper;

    public List<AuthUserWebAuthn> selectList(final LambdaQueryWrapper<AuthUserWebAuthn> wrapper) {
        return webAuthnMapper.selectList(wrapper);
    }
}
