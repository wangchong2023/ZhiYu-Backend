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
@TableName("refund_record")
public class RefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("refund_no")
    private String refundNo;

    @TableField("order_id")
    private Long orderId;

    @TableField("user_id")
    private Long userId;

    @TableField("amount")
    private Integer amount;

    @TableField("reason")
    private String reason;

    @TableField("description")
    private String description;

    @TableField("status")
    private String status;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("review_note")
    private String reviewNote;

    @TableField("channel_refund_id")
    private String channelRefundId;

    @TableField("applied_at")
    private LocalDateTime appliedAt;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("refunded_at")
    private LocalDateTime refundedAt;
}
