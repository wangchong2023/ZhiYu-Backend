package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "应用日志行")
public class AppLogDto {
    @Schema(description = "日志ID") private Long id;
    @Schema(description = "日志时间") private LocalDateTime time;
    @Schema(description = "级别") private String level;
    @Schema(description = "Logger") private String logger;
    @Schema(description = "消息") private String message;
    @Schema(description = "模块") private String module;
    @Schema(description = "Trace ID") private String traceId;
}
