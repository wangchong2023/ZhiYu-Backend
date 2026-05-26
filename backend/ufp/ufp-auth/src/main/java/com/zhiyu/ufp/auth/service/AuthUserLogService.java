package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.common.datasource.UfpDS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@UfpDS("ufp_auth")
public class AuthUserLogService {

    private final AuthUserLogMapper authUserLogMapper;

    public Page<AuthUserLog> selectPage(final Page<AuthUserLog> page,
                                         final LambdaQueryWrapper<AuthUserLog> wrapper) {
        return authUserLogMapper.selectPage(page, wrapper);
    }

    public List<AuthUserLog> selectList(final LambdaQueryWrapper<AuthUserLog> wrapper) {
        return authUserLogMapper.selectList(wrapper);
    }

    public int insert(final AuthUserLog log) {
        return authUserLogMapper.insert(log);
    }
}
