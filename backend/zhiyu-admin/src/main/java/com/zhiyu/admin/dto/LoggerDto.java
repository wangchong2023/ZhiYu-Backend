package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Logger 信息")
public class LoggerDto {
    @Schema(description = "Logger 名称") private String name;
    @Schema(description = "当前日志级别") private String configuredLevel;
    @Schema(description = "生效级别") private String effectiveLevel;

    @Data
    @Builder
    @Schema(description = "日志级别调整历史")
    public static class LogLevelHistoryDto {
        private Long id;
        private String loggerName;
        private String oldLevel;
        private String newLevel;
        private String changedBy;
        private LocalDateTime expireAt;
        private LocalDateTime rolledBackAt;
        private LocalDateTime createdAt;
    }
}
