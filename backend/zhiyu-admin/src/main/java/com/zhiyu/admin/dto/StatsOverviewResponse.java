package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘概览数据")
public class StatsOverviewResponse {
    @Schema(description = "今日注册数") private long todayRegistrations;
    @Schema(description = "今日登录数") private long todayLogins;
    @Schema(description = "今日活跃用户数 (DAU)") private long dau;
    @Schema(description = "近30天登录成功率 (百分比)") private double loginSuccessRate;
    @Schema(description = "注册较昨日变化百分比") private double registrationChange;
    @Schema(description = "登录较昨日变化百分比") private double loginChange;
}
