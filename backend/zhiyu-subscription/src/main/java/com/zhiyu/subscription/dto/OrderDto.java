package com.zhiyu.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单信息")
public class OrderDto {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "套餐ID")
    private Long planId;

    @Schema(description = "套餐标识")
    private String planKey;

    @Schema(description = "订阅周期")
    private String period;

    @Schema(description = "支付金额（分）")
    private Integer amount;

    @Schema(description = "原价（分）")
    private Integer originalAmount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "支付渠道")
    private String channel;

    @Schema(description = "订单状态")
    private String status;

    @Schema(description = "第三方交易ID")
    private String transactionId;

    @Schema(description = "预支付信息JSON")
    private String prepayInfoJson;

    @Schema(description = "支付时间")
    private LocalDateTime paidAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
