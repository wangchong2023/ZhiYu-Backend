package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.admin.service.AdminStatsService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台-仪表盘", description = "统计数据")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @Operation(summary = "仪表盘概览", description = "今日注册/登录/DAU/成功率")
    @GetMapping("/overview")
    public ApiResponse<StatsOverviewResponse> overview() {
        return ApiResponse.success(adminStatsService.getOverview());
    }

    @Operation(summary = "注册趋势", description = "近N天每日注册量")
    @GetMapping("/register-trend")
    public ApiResponse<List<TrendPoint>> registerTrend(
            @RequestParam(defaultValue = "30") final int days) {
        return ApiResponse.success(adminStatsService.getRegisterTrend(days));
    }

    @Operation(summary = "DAU趋势", description = "近N天每日活跃用户")
    @GetMapping("/dau-trend")
    public ApiResponse<List<TrendPoint>> dauTrend(
            @RequestParam(defaultValue = "30") final int days) {
        return ApiResponse.success(adminStatsService.getDauTrend(days));
    }

    @Operation(summary = "登录方式分布", description = "近N天各登录方式占比")
    @GetMapping("/login-method-dist")
    public ApiResponse<List<DistributionItem>> loginMethodDist(
            @RequestParam(defaultValue = "30") final int days) {
        return ApiResponse.success(adminStatsService.getLoginMethodDist(days));
    }

    @Operation(summary = "趋势数据", description = "近N天每日新增用户 + 活跃用户")
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "7") final int days) {
        return ApiResponse.success(adminStatsService.getTrend(days));
    }
}
