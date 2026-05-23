package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "注册响应")
public class RegisterResponse {
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;
}
