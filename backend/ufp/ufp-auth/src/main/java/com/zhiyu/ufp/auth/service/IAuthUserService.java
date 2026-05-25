package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.common.annotation.UfpClient;

import java.util.Collection;
import java.util.List;

@UfpClient(name = "ufp-auth", path = "/api/v1/ufp/auth/users")
public interface IAuthUserService {

    AuthUser selectById(Long id);

    List<AuthUser> selectByIds(Collection<Long> ids);

    AuthUser selectOne(LambdaQueryWrapper<AuthUser> wrapper);

    Page<AuthUser> selectPage(Page<AuthUser> page, LambdaQueryWrapper<AuthUser> wrapper);

    long selectCount(LambdaQueryWrapper<AuthUser> wrapper);

    int insert(AuthUser user);

    int updateById(AuthUser user);
}
