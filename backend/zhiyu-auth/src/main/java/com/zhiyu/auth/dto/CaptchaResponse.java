package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "验证码响应")
public class CaptchaResponse {
    @Schema(description = "验证码Token（用于后续注册/登录请求）")
    private String captchaToken;

    @Schema(description = "验证码图片（Base64编码，可直接嵌入 img 标签）",
            example = "data:image/png;base64,iVBORw0...")
    private String captchaImage;
}
