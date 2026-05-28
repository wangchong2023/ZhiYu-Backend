/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.user.controller;

import com.zhiyu.auth.service.IdentityService;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.entity.AuthUserWebAuthn;
import com.zhiyu.ufp.auth.service.AuthWebAuthnService;
import com.zhiyu.user.dto.WebAuthnCredentialDto;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户绑定认证身份控制层。
 *
 * <p>主要提供查询当前登录用户已绑定的三方社交登录方式、解绑指定的社交身份以及查询 WebAuthn 
 * 通行密钥（Passkeys）等涉及账户安全底座的交互功能。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Tag(name = "认证身份", description = "查看和解除绑定的认证方式")
@RestController
@RequestMapping("/api/v1/user")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class UserIdentityController {

    private final IdentityService identityService;
    private final AuthWebAuthnService authWebAuthnService;

    /**
     * 获取已绑定的认证方式。
     *
     * <p>查询并列出当前登录用户在账户系统中所有已绑定的第三方社交或外部认证方式（如微信、GitHub 等）。</p>
     *
     * @return 统一 API 响应体，内部包含已绑定的三方认证身份列表
     */
    @Operation(summary = "获取已绑定的认证方式", description = "列出当前用户所有已绑定的第三方登录方式")
    @GetMapping("/identities")
    public ApiResponse<List<AuthUserIdentity>> listIdentities() {
        // 调用底层安全底座身份服务，提取当前用户的身份列表
        return ApiResponse.success(identityService.listIdentities(getCurrentUserId()));
    }

    /**
     * 解除指定的外部认证绑定。
     *
     * <p>解开绑定的第三方登录身份。此操作将进行防御性拦截：若目标身份是用户唯一的登录手段，
     * 系统将抛出异常以防用户陷入无法登陆的“死锁账户”状态。</p>
     *
     * @param identityId 待解绑的三方身份记录主键 ID
     * @return 统一 API 响应体，操作成功时数据域为 null
     */
    @Operation(summary = "解绑认证方式", description = "解除绑定指定的第三方登录方式（不得解绑最后一种方式）")
    @DeleteMapping("/identities/{identityId}")
    public ApiResponse<Void> unbindIdentity(@PathVariable("identityId") final Long identityId) {
        // 执行解绑核心逻辑并校验边界防御条件
        identityService.unbindIdentity(getCurrentUserId(), identityId);
        return ApiResponse.success(null);
    }

    /**
     * 获取通行密钥凭证列表。
     *
     * <p>查询并返回当前登录用户在系统中已注册且处于启用状态的 WebAuthn 通行密钥凭证信息。</p>
     *
     * @return 统一 API 响应体，内部包含通行密钥凭证 DTO 列表
     */
    @Operation(summary = "获取通行密钥列表", description = "返回当前用户已注册的 WebAuthn 通行密钥")
    @GetMapping("/webauthn/credentials")
    public ApiResponse<List<WebAuthnCredentialDto>> listWebAuthnCredentials() {
        Long userId = getCurrentUserId();
        // 1. 从数据表检索属于当前用户、且状态正常的 WebAuthn 凭证实体集
        List<AuthUserWebAuthn> entities = authWebAuthnService.selectList(
                new LambdaQueryWrapper<AuthUserWebAuthn>()
                        .eq(AuthUserWebAuthn::getAuthUserId, userId)
                        .eq(AuthUserWebAuthn::getEnabled, 1));
        
        // 2. 将实体流式装配映射为表现层 DTO 视图对象
        return ApiResponse.success(entities.stream()
                .map(e -> WebAuthnCredentialDto.builder()
                        .credentialId(e.getCredentialId())
                        .deviceName(e.getDeviceName())
                        .createdAt(e.getCreatedTime())
                        .lastUsedTime(e.getLastUsedTime())
                        .build())
                .collect(Collectors.toList()));
    }

    /**
     * 从 Spring Security 认证上下文中提取当前登录用户的 ID。
     *
     * @return 当前登录的认证用户主键 ID
     */
    private Long getCurrentUserId() {
        // 从 Security 上下文解析 Principal 属性，转换为 Long 类型用户 ID
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
