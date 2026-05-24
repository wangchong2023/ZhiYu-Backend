package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "慢查询日志行")
public class SlowQueryDto {
    @Schema(description = "ID") private Long id;
    @Schema(description = "发生时间") private LocalDateTime time;
    @Schema(description = "SQL摘要") private String sqlSummary;
    @Schema(description = "耗时ms") private Long durationMs;
    @Schema(description = "来源") private String source;
}
