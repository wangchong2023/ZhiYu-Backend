package com.zhiyu.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订阅信息")
public class SubscriptionDto {

    @Schema(description = "用户订阅ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "套餐ID")
    private Long planId;

    @Schema(description = "套餐名称")
    private String planName;

    @Schema(description = "订阅状态")
    private String status;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "是否自动续费")
    private Integer autoRenew;

    @Schema(description = "待降级套餐")
    private String pendingDowngrade;

    @Schema(description = "是否已使用试用")
    private Integer trialUsed;

    @Schema(description = "取消时间")
    private LocalDateTime cancelledAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
