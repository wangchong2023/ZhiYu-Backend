package com.zhiyu.ufp.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import com.zhiyu.ufp.auth.mapper.AuthOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthOperationLogService {

    private final AuthOperationLogMapper authOperationLogMapper;

    public Page<AuthOperationLog> selectPage(final Page<AuthOperationLog> page,
                                              final LambdaQueryWrapper<AuthOperationLog> wrapper) {
        return authOperationLogMapper.selectPage(page, wrapper);
    }
}
