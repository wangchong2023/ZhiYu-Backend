/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

/**
 * 授权用户服务二方 Feign 客户端。
 *
 * <p>面向核心授权模块的用户查询交互接口。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@FeignClient(contextId = "authUserClient", name = "${zhiyu.feign.auth-service.name:zhiyu-backend}", path = "/api/v1/auth/users")
public interface AuthUserClient {

    /**
     * 查询指定用户ID的基本授权档案信息。
     *
     * @param userId 用户的全局唯一 ID
     * @return 用户基本属性及角色信息的键值对 Map
     */
    @GetMapping("/{userId}")
    Map<String, Object> getUserInfo(@PathVariable("userId") Long userId);
}
