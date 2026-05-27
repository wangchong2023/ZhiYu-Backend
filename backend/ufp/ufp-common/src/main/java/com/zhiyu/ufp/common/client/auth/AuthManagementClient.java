/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

/**
 * 授权管理服务二方 Feign 客户端。
 *
 * <p>面向核心授权模块（auth-service）的令牌/会话管理交互接口。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@FeignClient(contextId = "authManagementClient", name = "${zhiyu.feign.auth-service.name:zhiyu-backend}", path = "/api/v1/auth/manage")
public interface AuthManagementClient {

    /**
     * 校验指定的访问令牌（Access Token），并提取其 Payload 数据。
     *
     * @param token 待验证的 JWT 令牌（不包含 Bearer 前缀）
     * @return 包含 Claims 和租户/角色信息的键值对 Map
     */
    @GetMapping("/validate-token")
    Map<String, Object> validateToken(@RequestParam("token") String token);
}
