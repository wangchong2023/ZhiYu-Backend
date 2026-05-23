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
@TableName("auth_user_identity")
public class AuthUserIdentity {

    @TableId(type = IdType.AUTO)
    private Long authUserIdentityId;

    @TableField("auth_user_id")
    private Long authUserId;

    @TableField("provider")
    private String provider;

    @TableField("openid")
    private String openid;

    @TableField("unionid")
    private String unionid;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("credential")
    private String credential;

    @TableField("refresh_token")
    private String refreshToken;

    @TableField("token_expire")
    private LocalDateTime tokenExpire;

    @TableField("enabled")
    private Integer enabled;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
