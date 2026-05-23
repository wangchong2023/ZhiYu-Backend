package com.zhiyu.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "WebAuthn 注册/认证开始响应")
public class WebAuthnResponse {

    @Schema(description = "挑战会话ID（finish 时回传）")
    private String challengeId;

    @Schema(description = "WebAuthn 创建选项 JSON（传给 navigator.credentials.create）")
    private String optionsJson;
}
