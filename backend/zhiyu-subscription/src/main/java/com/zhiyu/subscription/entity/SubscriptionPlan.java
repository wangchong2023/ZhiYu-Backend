package com.zhiyu.subscription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("subscription_plan")
public class SubscriptionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_key")
    private String planKey;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("price_monthly")
    private Integer priceMonthly;

    @TableField("price_yearly")
    private Integer priceYearly;

    @TableField("trial_days")
    private Integer trialDays;

    @TableField("features_json")
    private String featuresJson;

    @TableField("quotas_json")
    private String quotasJson;

    @TableField("is_active")
    private Integer isActive;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
