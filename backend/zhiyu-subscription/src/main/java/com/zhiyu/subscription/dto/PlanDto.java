package com.zhiyu.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "套餐信息")
public class PlanDto {

    @Schema(description = "套餐ID")
    private Long id;

    @Schema(description = "套餐标识")
    private String planKey;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐描述")
    private String description;

    @Schema(description = "月度价格（分）")
    private Integer priceMonthly;

    @Schema(description = "年度价格（分）")
    private Integer priceYearly;

    @Schema(description = "试用天数")
    private Integer trialDays;

    @Schema(description = "功能列表JSON")
    private String featuresJson;

    @Schema(description = "配额JSON")
    private String quotasJson;
}
