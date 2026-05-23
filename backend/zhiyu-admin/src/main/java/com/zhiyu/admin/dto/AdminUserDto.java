package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户列表行")
public class AdminUserDto {
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "用户名") private String username;
    @Schema(description = "邮箱") private String email;
    @Schema(description = "手机") private String mobile;
    @Schema(description = "注册时间") private LocalDateTime createdAt;
    @Schema(description = "状态") private String status;
    @Schema(description = "最后登录时间") private LocalDateTime lastLoginAt;
    @Schema(description = "最后登录IP") private String lastLoginIp;
}
