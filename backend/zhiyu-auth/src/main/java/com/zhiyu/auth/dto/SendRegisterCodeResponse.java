package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "发送注册验证码响应")
public class SendRegisterCodeResponse {

    @Schema(description = "验证码有效期（分钟）", example = "5")
    private int expireMinutes;

    @Schema(description = "重试等待时间（秒）", example = "60")
    private int retryAfterSeconds;
}
