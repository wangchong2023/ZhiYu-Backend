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
@TableName("auth_user")
public class AuthUser {

    @TableId(type = IdType.AUTO)
    private Long authUserId;

    @TableField("auth_user_code")
    private String authUserCode;

    @TableField("auth_user_nick")
    private String authUserNick;

    @TableField("auth_user_username")
    private String authUserUsername;

    @TableField("auth_user_username_login_enable")
    private Integer authUserUsernameLoginEnable;

    @TableField("auth_user_mail")
    private String authUserMail;

    @TableField("auth_user_mail_verified")
    private Integer authUserMailVerified;

    @TableField("auth_user_mail_login_enable")
    private Integer authUserMailLoginEnable;

    @TableField("auth_user_mobile")
    private String authUserMobile;

    @TableField("auth_user_mobile_verified")
    private Integer authUserMobileVerified;

    @TableField("auth_user_mobile_login_enable")
    private Integer authUserMobileLoginEnable;

    @TableField("auth_user_password")
    private String authUserPassword;

    @TableField("auth_user_password_salt")
    private String authUserPasswordSalt;

    @TableField("auth_user_password_expire")
    private LocalDateTime authUserPasswordExpire;

    @TableField("auth_user_password_history")
    private String authUserPasswordHistory;

    @TableField("auth_user_enable")
    private Integer authUserEnable;

    @TableField("auth_user_enable_expire")
    private LocalDateTime authUserEnableExpire;

    @TableField("auth_user_deleted")
    private Integer authUserDeleted;

    @TableField("auth_user_avatar")
    private String authUserAvatar;

    @TableField("auth_user_scope")
    private String authUserScope;

    @TableField("created_user")
    private String createdUser;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_user")
    private String updatedUser;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
