package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "第三方登录请求")
public class OAuthLoginRequest {

    @NotBlank
    @Schema(description = "第三方授权码", example = "081xYz0w3QnP1L1t4J0w3nZyDG1xYz0F")
    private String code;

    @Schema(description = "OAuth state 参数（防 CSRF）")
    private String state;

    @Schema(description = "ID Token（Apple/Google 场景使用）")
    private String idToken;
}
