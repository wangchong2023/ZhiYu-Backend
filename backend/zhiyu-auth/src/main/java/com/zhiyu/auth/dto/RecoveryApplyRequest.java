package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "账户恢复申请请求")
public class RecoveryApplyRequest {

    @Schema(description = "注册邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "绑定手机号", example = "13812345678")
    private String phone;

    @Schema(description = "用户名", example = "testuser")
    private String username;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "申请原因", example = "丢失所有设备，无法登录")
    private String reason;
}
