package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "组件健康状态")
public class HealthDto {
    @Schema(description = "组件名称", example = "MySQL") private String component;
    @Schema(description = "状态: UP / DOWN / DEGRADED") private String status;
    @Schema(description = "实例数") private int instanceCount;
    @Schema(description = "响应时间 ms (数据库)") private Long responseTimeMs;
    @Schema(description = "详情") private String detail;
}
