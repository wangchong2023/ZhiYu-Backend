package com.zhiyu.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建订单请求")
public class CreateOrderRequest {

    @NotNull(message = "planId must not be null")
    @Schema(description = "套餐ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long planId;

    @NotNull(message = "period must not be null")
    @Schema(description = "订阅周期：MONTHLY / YEARLY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String period;

    @NotNull(message = "channel must not be null")
    @Schema(description = "支付渠道：WECHAT / ALIPAY / APPLE / GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channel;
}
