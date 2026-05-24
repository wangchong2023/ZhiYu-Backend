package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "恢复后重置密码请求")
public class RecoveryResetRequest {

    @NotBlank
    @Schema(description = "恢复令牌（来自恢复链接）")
    private String recoveryToken;

    @NotBlank
    @Size(min = 8, max = 64)
    @Schema(description = "新密码", example = "NewAbc12345")
    private String newPassword;
}
