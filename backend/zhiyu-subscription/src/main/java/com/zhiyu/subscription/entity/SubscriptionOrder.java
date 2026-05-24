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
@TableName("subscription_order")
public class SubscriptionOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("plan_id")
    private Long planId;

    @TableField("plan_key")
    private String planKey;

    @TableField("period")
    private String period;

    @TableField("amount")
    private Integer amount;

    @TableField("original_amount")
    private Integer originalAmount;

    @TableField("currency")
    private String currency;

    @TableField("channel")
    private String channel;

    @TableField("status")
    private String status;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("prepay_info_json")
    private String prepayInfoJson;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
