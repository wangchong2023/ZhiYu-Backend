package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@Schema(description = "监控指标数据")
public class MetricsDto {
    @Schema(description = "QPS 时序数据") private List<MetricPoint> qps;
    @Schema(description = "P50 延迟时序") private List<MetricPoint> latencyP50;
    @Schema(description = "P95 延迟时序") private List<MetricPoint> latencyP95;
    @Schema(description = "P99 延迟时序") private List<MetricPoint> latencyP99;
    @Schema(description = "错误率时序") private List<MetricPoint> errorRate;

    @Data
    @Builder
    @Schema(description = "指标时序点")
    public static class MetricPoint {
        @Schema(description = "时间戳 (epoch seconds)") private long timestamp;
        @Schema(description = "值") private double value;
    }
}
