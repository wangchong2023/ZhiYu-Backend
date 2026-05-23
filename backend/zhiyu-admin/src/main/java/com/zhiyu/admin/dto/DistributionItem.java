package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "分布数据项")
public class DistributionItem {
    @Schema(description = "登录方式") private String method;
    @Schema(description = "数量") private long count;
    @Schema(description = "百分比") private double percentage;
}
