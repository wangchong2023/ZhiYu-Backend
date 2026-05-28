/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AuthUserServiceTest.java
 * 创建时间: 2026-05-28
 * 描述: AuthUserService 核心用户服务中语言偏好设置更新的 Mockito 单元测试。
 */
package com.zhiyu.ufp.auth.service;

import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类名: AuthUserServiceTest
 * 描述: 测试用户的多语言偏好字段（auth_user_personal_locale）在持久化更新时的业务处理与防御性异常抛出。
 */
public class AuthUserServiceTest {

    @Mock
    private AuthUserMapper authUserMapper;

    @InjectMocks
    private AuthUserService authUserService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testUpdatePersonalLocaleSuccess() {
        // 1. 模拟用户存在
        AuthUser user = new AuthUser();
        user.setAuthUserId(1001L);
        user.setAuthUserUsername("Constantine");

        when(authUserMapper.selectById(1001L)).thenReturn(user);
        when(authUserMapper.updateById(any(AuthUser.class))).thenReturn(1);

        // 2. 执行更新
        int result = authUserService.updatePersonalLocale(1001L, "en_US");

        // 3. 校验结果
        assertEquals(1, result);
        assertEquals("en_US", user.getAuthUserPersonalLocale());
        verify(authUserMapper).updateById(user);
    }

    @Test
    public void testUpdatePersonalLocaleUserNotFound() {
        // 1. 模拟用户不存在
        when(authUserMapper.selectById(9999L)).thenReturn(null);

        // 2. 校验是否正确抛出业务异常
        assertThrows(BizException.class, () -> {
            authUserService.updatePersonalLocale(9999L, "zh_CN");
        });
    }
}
