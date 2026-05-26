package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.mapper.AuthUserIdentityMapper;
import com.zhiyu.ufp.common.datasource.UfpDS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@UfpDS("ufp_auth")
public class AuthUserIdentityService {

    private final AuthUserIdentityMapper identityMapper;

    public Page<AuthUserIdentity> selectPage(final Page<AuthUserIdentity> page,
                                              final LambdaQueryWrapper<AuthUserIdentity> wrapper) {
        return identityMapper.selectPage(page, wrapper);
    }
}
