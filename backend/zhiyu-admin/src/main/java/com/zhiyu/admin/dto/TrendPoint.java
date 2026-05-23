package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "趋势数据点")
public class TrendPoint {
    @Schema(description = "日期", example = "2026-05-23") private String date;
    @Schema(description = "数量") private long count;
}
