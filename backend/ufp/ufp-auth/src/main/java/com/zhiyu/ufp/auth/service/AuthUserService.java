package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final AuthUserMapper authUserMapper;

    public AuthUser selectById(final Long id) {
        return authUserMapper.selectById(id);
    }

    public AuthUser selectOne(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectOne(wrapper);
    }

    public Page<AuthUser> selectPage(final Page<AuthUser> page,
                                      final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectPage(page, wrapper);
    }

    public long selectCount(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectCount(wrapper);
    }

    public int insert(final AuthUser user) {
        return authUserMapper.insert(user);
    }

    public int updateById(final AuthUser user) {
        return authUserMapper.updateById(user);
    }
}
