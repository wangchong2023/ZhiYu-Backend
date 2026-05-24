package com.zhiyu.auth.entity;

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
@TableName("account_recovery_ticket")
public class AccountRecoveryTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_no")
    private String ticketNo;

    @TableField("user_id")
    private Long userId;

    @TableField("submitted_info")
    private String submittedInfo;

    @TableField("status")
    private String status;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("review_note")
    private String reviewNote;

    @TableField("recovery_token")
    private String recoveryToken;

    @TableField("recovery_token_expires")
    private LocalDateTime recoveryTokenExpires;

    @TableField("applied_at")
    private LocalDateTime appliedAt;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;
}
