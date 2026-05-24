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

@Tag(name = "用户资料", description = "个人信息查看、编辑与账号注销")
@RestController
@RequestMapping("/api/v1/user")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "获取个人资料", description = "返回当前登录用户的完整个人资料")
    @GetMapping("/profile")
    public ApiResponse<UserProfileResp> profile() {
        return ApiResponse.success(userProfileService.getProfile(getCurrentUserId()));
    }

    @Operation(summary = "更新个人资料", description = "修改昵称等非敏感字段（邮箱/手机变更需验证码）")
    @PutMapping("/profile")
    public ApiResponse<UserProfileResp> update(@Valid @RequestBody final UpdateProfileReq request) {
        return ApiResponse.success(userProfileService.updateProfile(getCurrentUserId(), request));
    }

    @Operation(summary = "登录历史", description = "返回当前用户最近的登录记录")
    @GetMapping("/login-history")
    public ApiResponse<Page<LoginHistoryDto>> loginHistory(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return ApiResponse.success(userProfileService.getLoginHistory(
                getCurrentUserId(), page, size));
    }

    @Operation(summary = "上传头像", description = "上传用户头像图片（支持 PNG/JPG/GIF）")
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> uploadAvatar(@RequestParam("file") final MultipartFile file) {
        return ApiResponse.success(userProfileService.uploadAvatar(
                getCurrentUserId(), file));
    }

    @Operation(summary = "获取头像", description = "返回用户头像图片")
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable final Long userId) {
        return userProfileService.getAvatar(userId);
    }

    @Operation(summary = "注销账号", description = "软删除账号，30 天内可恢复")
    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount() {
        userProfileService.deleteAccount(getCurrentUserId());
        return ApiResponse.success(null);
    }

    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
