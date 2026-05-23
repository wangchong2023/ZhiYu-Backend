package com.zhiyu.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "登录/刷新响应")
public class LoginResponse {

    @Schema(description = "访问令牌（JWT RS256，15分钟有效）")
    private String accessToken;

    @Schema(description = "刷新令牌（7天有效，一次性使用）")
    private String refreshToken;

    @Schema(description = "Access Token 有效期（秒）", example = "900")
    private long expiresIn;

    @Schema(description = "Token 类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "是否需要 TOTP 二次验证（P0 返回 false）")
    private Boolean totpRequired;

    @Schema(description = "是否新注册用户（第三方登录时返回）")
    private Boolean isNewUser;
}
