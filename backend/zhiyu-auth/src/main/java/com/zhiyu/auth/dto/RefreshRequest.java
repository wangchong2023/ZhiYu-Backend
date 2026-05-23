package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "刷新Token请求")
public class RefreshRequest {
    @NotBlank
    @Schema(description = "Refresh Token")
    private String refreshToken;
}
