package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "告警信息")
public class AlertDto {
    @Schema(description = "告警名称") private String alertName;
    @Schema(description = "严重级别: P0/P1/P2") private String severity;
    @Schema(description = "告警条件描述") private String condition;
    @Schema(description = "当前值") private String currentValue;
    @Schema(description = "状态: FIRING / RESOLVED") private String status;
    @Schema(description = "触发时间") private String firedAt;
}
