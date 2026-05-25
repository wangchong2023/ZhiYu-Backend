package com.zhiyu.ufp.auth.entity;

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
@TableName("auth_user_web_authn")
public class AuthUserWebAuthn {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long authUserWebAuthnId;

    @TableField("auth_user_id")
    private Long authUserId;

    @TableField("credential_id")
    private String credentialId;

    @TableField("public_key")
    private String publicKey;

    @TableField("sign_count")
    private Integer signCount;

    @TableField("device_name")
    private String deviceName;

    @TableField("enabled")
    private Integer enabled;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("last_used_time")
    private LocalDateTime lastUsedTime;
}
