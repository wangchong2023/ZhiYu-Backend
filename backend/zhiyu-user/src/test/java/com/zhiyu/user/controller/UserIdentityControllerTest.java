/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: UserIdentityControllerTest.java
 * 创建时间: 2026-05-28
 * 描述: 用户认证身份与通行密钥控制器层单元测试。
 */
package com.zhiyu.user.controller;

import com.zhiyu.auth.service.IdentityService;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.service.AuthWebAuthnService;
import com.zhiyu.user.dto.WebAuthnCredentialDto;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 类名: UserIdentityControllerTest
 * 描述: 精确拦截 SecurityContextHolder 并 Mock 第三方绑定认证以及 WebAuthn 列表查询。
 */
@ExtendWith(MockitoExtension.class)
class UserIdentityControllerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private AuthWebAuthnService authWebAuthnService;

    private UserIdentityController controller;

    private MockedStatic<SecurityContextHolder> mockHolder;
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        // 实例化 Controller
        controller = new UserIdentityController(identityService, authWebAuthnService);

        // 初始化并 Mock 静态的 SecurityContext 上下文，强行注入 Mock 用户 ID: 1001
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        mockHolder = mockStatic(SecurityContextHolder.class);
        mockHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        // 关闭 Mock 静态流，释放资源以防止并发线程池污染
        mockHolder.close();
    }

    /**
     * 描述: 测试获取已绑定的第三方登录认证方式。
     */
    @Test
    void shouldReturnIdentitiesSuccessfully() {
        List<AuthUserIdentity> expected = new ArrayList<>();
        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(55L);
        identity.setAuthUserId(1001L);
        identity.setProvider("GITHUB");
        identity.setOpenid("github-user-id-abc");
        expected.add(identity);

        when(identityService.listIdentities(1001L)).thenReturn(expected);

        ApiResponse<List<AuthUserIdentity>> response = controller.listIdentities();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getProvider()).isEqualTo("GITHUB");
        verify(identityService).listIdentities(1001L);
    }

    /**
     * 描述: 测试解绑第三方认证方式。
     */
    @Test
    void shouldUnbindIdentitySuccessfully() {
        ApiResponse<Void> response = controller.unbindIdentity(55L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();
        verify(identityService).unbindIdentity(1001L, 55L);
    }

    /**
     * 描述: 测试获取 WebAuthn 通行密钥列表并成功转换 DTO 输出。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnWebAuthnCredentialsSuccessfully() {
        List<AuthUserWebAuthn> mockEntities = new ArrayList<>();
        AuthUserWebAuthn webAuthn = new AuthUserWebAuthn();
        webAuthn.setCredentialId("cred-12345");
        webAuthn.setDeviceName("iPhone Pro");
        LocalDateTime created = LocalDateTime.of(2026, 5, 28, 10, 0);
        LocalDateTime lastUsed = LocalDateTime.of(2026, 5, 28, 18, 0);
        webAuthn.setCreatedTime(created);
        webAuthn.setLastUsedTime(lastUsed);
        mockEntities.add(webAuthn);

        when(authWebAuthnService.selectList(any(LambdaQueryWrapper.class))).thenReturn(mockEntities);

        ApiResponse<List<WebAuthnCredentialDto>> response = controller.listWebAuthnCredentials();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).hasSize(1);
        
        WebAuthnCredentialDto dto = response.getData().get(0);
        assertThat(dto.getCredentialId()).isEqualTo("cred-12345");
        assertThat(dto.getDeviceName()).isEqualTo("iPhone Pro");
        assertThat(dto.getCreatedAt()).isEqualTo(created);
        assertThat(dto.getLastUsedTime()).isEqualTo(lastUsed);
    }
}
