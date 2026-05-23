package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_login_attempt")
public class AuthLoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long authLoginAttemptId;
    @TableField("identifier")
    private String identifier;
    @TableField("attempt_type")
    private String attemptType;
    @TableField("success")
    private Integer success;
    @TableField("failure_reason")
    private String failureReason;
    @TableField("source_ip")
    private String sourceIp;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
