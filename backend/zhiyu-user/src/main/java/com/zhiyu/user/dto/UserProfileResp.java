package com.zhiyu.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户资料响应")
public class UserProfileResp {

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nick;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "邮箱是否已验证")
    private boolean emailVerified;

    @Schema(description = "手机号", example = "13800138000")
    private String mobile;

    @Schema(description = "手机是否已验证")
    private boolean mobileVerified;

    @Schema(description = "账号权限范围", example = "openid")
    private String scope;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;
}
