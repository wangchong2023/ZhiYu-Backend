package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_user_log")
public class AuthUserLog {
    @TableId(type = IdType.AUTO)
    private Long authUserLogId;
    @TableField("auth_user_log_user_id")
    private Long authUserLogUserId;
    @TableField("auth_user_log_user_display")
    private String authUserLogUserDisplay;
    @TableField("auth_user_log_location")
    private String authUserLogLocation;
    @TableField("auth_user_log_ip")
    private String authUserLogIp;
    @TableField("auth_user_log_browse")
    private String authUserLogBrowse;
    @TableField("auth_user_log_device")
    private String authUserLogDevice;
    @TableField("auth_user_log_action")
    private String authUserLogAction;
    @TableField("auth_user_log_type")
    private String authUserLogType;
    @TableField("auth_user_log_result")
    private String authUserLogResult;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
