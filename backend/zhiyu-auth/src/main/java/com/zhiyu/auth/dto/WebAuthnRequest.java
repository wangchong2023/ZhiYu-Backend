package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "WebAuthn 请求")
public class WebAuthnRequest {

    @NotBlank
    @Schema(description = "挑战会话ID（begin 接口返回的 challengeId）")
    private String challengeId;

    @NotBlank
    @Schema(description = "浏览器 WebAuthn API 返回的凭证 JSON")
    private String credentialJson;

    @Schema(description = "用户名（authenticate/begin 时使用）")
    private String username;
}
