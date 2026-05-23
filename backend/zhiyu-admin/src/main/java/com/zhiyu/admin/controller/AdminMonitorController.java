package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.admin.service.AdminMonitorService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台-运行监控", description = "服务健康/指标/告警/日志级别")
@RestController
@RequestMapping("/api/v1/admin/monitor")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    @Operation(summary = "服务健康概览")
    @GetMapping("/health")
    public ApiResponse<List<HealthDto>> health() {
        return ApiResponse.success(adminMonitorService.getHealth());
    }

    @Operation(summary = "API 指标", description = "range: 1h, 6h, 24h, 7d")
    @GetMapping("/metrics")
    public ApiResponse<MetricsDto> metrics(
            @RequestParam(defaultValue = "24h") String range) {
        return ApiResponse.success(adminMonitorService.getMetrics(range));
    }

    @Operation(summary = "告警列表", description = "可选筛选: status=FIRING|RESOLVED, severity=P0|P1|P2")
    @GetMapping("/alerts")
    public ApiResponse<List<AlertDto>> alerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(adminMonitorService.getAlerts(
                status, severity, startTime, endTime));
    }

    @Operation(summary = "最近告警", description = "最近 FIRING 告警，仪表盘用")
    @GetMapping("/alerts/recent")
    public ApiResponse<List<AlertDto>> recentAlerts() {
        return ApiResponse.success(adminMonitorService.getAlerts(
                "firing", null, null, null));
    }

    @Operation(summary = "Logger 列表")
    @GetMapping("/loggers")
    public ApiResponse<List<LoggerDto>> loggers() {
        return ApiResponse.success(adminMonitorService.getLoggers());
    }

    @Operation(summary = "修改 Logger 级别")
    @PostMapping("/loggers/{name}")
    public ApiResponse<Void> setLoggerLevel(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        String configuredLevel = body.get("configuredLevel");
        adminMonitorService.setLoggerLevel(name, configuredLevel);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Logger 调整历史")
    @GetMapping("/loggers/history")
    public ApiResponse<List<LoggerDto.LogLevelHistoryDto>> loggerHistory() {
        return ApiResponse.success(List.of());
    }
}
