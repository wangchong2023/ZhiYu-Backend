package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("auth_user_device")
public class AuthUserDevice {
    @TableId(type = IdType.AUTO)
    private Long authUserDeviceId;
    @TableField("auth_user_id")
    private Long authUserId;
    @TableField("device_id")
    private String deviceId;
    @TableField("platform")
    private String platform;
    @TableField("trusted_for_totp")
    private Integer trustedForTotp;
    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
