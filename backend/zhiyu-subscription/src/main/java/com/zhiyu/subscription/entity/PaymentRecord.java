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
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("user_id")
    private Long userId;

    @TableField("channel")
    private String channel;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("amount")
    private Integer amount;

    @TableField("currency")
    private String currency;

    @TableField("status")
    private String status;

    @TableField("raw_notification")
    private String rawNotification;

    @TableField("reconciliation_status")
    private String reconciliationStatus;

    @TableField("reconciliation_note")
    private String reconciliationNote;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
