/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: UserProfileController.java
 * 创建时间: 2026-05-27
 * 描述: 用户资料表现层控制器，提供个人基本资料查看、更新、头像上传/下载及账户安全软删除注销能力。
 */
package com.zhiyu.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.user.dto.LoginHistoryDto;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import com.zhiyu.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 类名: UserProfileController
 * 描述: 用户资料管理控制器。该类满足 Controller 薄层规范，仅对入参进行校验，不直接编写任何业务逻辑与数据库访问逻辑。
 */
@Tag(name = "用户资料", description = "个人信息查看、编辑与账号注销")
@RestController
@RequestMapping("/api/v1/user")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 描述: 获取当前登录用户的个人资料
     * @return 包含用户详细资料的统一响应结果 ApiResponse
     */
    @Operation(summary = "获取个人资料", description = "返回当前登录用户的完整个人资料")
    @GetMapping("/profile")
    public ApiResponse<UserProfileResp> profile() {
        return ApiResponse.success(userProfileService.getProfile(getCurrentUserId()));
    }

    /**
     * 描述: 更新当前登录用户的个人信息
     * @param request 包含昵称、头像路径等属性的请求对象 UpdateProfileReq
     * @return 包含更新后详细资料的统一响应结果 ApiResponse
     */
    @Operation(summary = "更新个人资料", description = "修改昵称等非敏感字段（邮箱/手机变更需验证码）")
    @PutMapping("/profile")
    public ApiResponse<UserProfileResp> update(@Valid @RequestBody final UpdateProfileReq request) {
        return ApiResponse.success(userProfileService.updateProfile(getCurrentUserId(), request));
    }

    /**
     * 描述: 获取当前登录用户的登录历史记录（支持分页）
     * @param page 页码，默认值为 1
     * @param size 每页大小，默认值为 20
     * @return 包含分页历史数据的统一响应结果 ApiResponse
     */
    @Operation(summary = "登录历史", description = "返回当前用户最近的登录记录")
    @GetMapping("/login-history")
    public ApiResponse<Page<LoginHistoryDto>> loginHistory(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return ApiResponse.success(userProfileService.getLoginHistory(
                getCurrentUserId(), page, size));
    }

    /**
     * 描述: 上传并设置当前登录用户的头像图片
     * @param file 头像文件（支持 PNG/JPG/GIF，限 2MB）
     * @return 包含上传头像后访问路径的统一响应结果 ApiResponse
     */
    @Operation(summary = "上传头像", description = "上传用户头像图片（支持 PNG/JPG/GIF）")
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadAvatar(@RequestParam("file") final MultipartFile file) {
        return ApiResponse.success(userProfileService.uploadAvatar(
                getCurrentUserId(), file));
    }

    /**
     * 描述: 获取指定用户的头像静态资源，支持断点下载或预览
     * @param userId 用户唯一标识 ID
     * @return 包含文件流和 Content-Type 属性的资源实体 ResponseEntity
     */
    @Operation(summary = "获取头像", description = "返回用户头像图片")
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable final Long userId) {
        return userProfileService.getAvatar(userId);
    }

    /**
     * 描述: 注销当前登录的账号，触发软删除机制，进入 30 天恢复冷却期
     * @return 空数据的成功响应 ApiResponse
     */
    @DeleteMapping("/account")
    @Operation(summary = "注销账号", description = "软删除账号，30 天内可恢复")
    public ApiResponse<Void> deleteAccount() {
        userProfileService.deleteAccount(getCurrentUserId());
        return ApiResponse.success(null);
    }

    /**
     * 描述: 私有辅助方法，从 Spring Security 认证上下文提取并解析当前登录的用户 ID
     * @return 当前登录用户的 Long 型唯一标识 ID
     */
    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}

