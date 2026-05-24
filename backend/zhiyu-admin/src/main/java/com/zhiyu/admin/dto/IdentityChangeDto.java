package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "身份变更审计行")
public class IdentityChangeDto {
    @Schema(description = "ID") private Long id;
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "操作") private String action;
    @Schema(description = "身份类型") private String identityType;
    @Schema(description = "来源IP") private String sourceIp;
    @Schema(description = "操作时间") private LocalDateTime createdAt;
}
