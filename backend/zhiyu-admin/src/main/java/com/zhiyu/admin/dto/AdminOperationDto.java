package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "管理员操作审计行")
public class AdminOperationDto {
    @Schema(description = "ID") private String id;
    @Schema(description = "操作用户") private String username;
    @Schema(description = "操作类型") private String action;
    @Schema(description = "操作目标") private String target;
    @Schema(description = "目标ID") private String targetId;
    @Schema(description = "IP地址") private String ip;
    @Schema(description = "操作时间") private LocalDateTime time;
}
