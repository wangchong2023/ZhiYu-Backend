package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "访问日志行")
public class AccessLogDto {
    @Schema(description = "ID") private Long id;
    @Schema(description = "请求时间") private LocalDateTime time;
    @Schema(description = "来源IP") private String ip;
    @Schema(description = "请求方法") private String method;
    @Schema(description = "请求路径") private String path;
    @Schema(description = "状态码") private Integer statusCode;
    @Schema(description = "响应时间ms") private Long responseTimeMs;
    @Schema(description = "User-Agent") private String userAgent;
}
