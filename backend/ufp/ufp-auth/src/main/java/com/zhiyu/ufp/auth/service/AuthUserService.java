package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthUserService implements IAuthUserService {

    private final AuthUserMapper authUserMapper;

    @Override
    public AuthUser selectById(final Long id) {
        return authUserMapper.selectById(id);
    }

    @Override
    public List<AuthUser> selectByIds(final Collection<Long> ids) {
        return authUserMapper.selectBatchIds(ids);
    }

    @Override
    public AuthUser selectOne(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectOne(wrapper);
    }

    @Override
    public Page<AuthUser> selectPage(final Page<AuthUser> page,
                                      final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectPage(page, wrapper);
    }

    @Override
    public long selectCount(final LambdaQueryWrapper<AuthUser> wrapper) {
        return authUserMapper.selectCount(wrapper);
    }

    @Override
    public int insert(final AuthUser user) {
        return authUserMapper.insert(user);
    }

    @Override
    public int updateById(final AuthUser user) {
        return authUserMapper.updateById(user);
    }
}
