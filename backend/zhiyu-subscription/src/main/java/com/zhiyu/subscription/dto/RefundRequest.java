package com.zhiyu.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "退款申请请求")
public class RefundRequest {

    @NotNull(message = "orderId must not be null")
    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @NotBlank(message = "reason must not be blank")
    @Schema(description = "退款原因：DUPLICATE_PURCHASE / ACCIDENTAL / NOT_SATISFIED / OTHER",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @Schema(description = "退款说明")
    private String description;
}
