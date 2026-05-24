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
@TableName("auth_grant")
public class AuthGrant {

    @TableId(type = IdType.AUTO)
    private Integer authGrantId;

    @TableField("auth_grant_owner_type")
    private Integer authGrantOwnerType;

    @TableField("auth_grant_owner_value")
    private String authGrantOwnerValue;

    @TableField("auth_grant_target_type")
    private Integer authGrantTargetType;

    @TableField("auth_grant_target_value")
    private String authGrantTargetValue;

    @TableField("auth_grant_action")
    private Integer authGrantAction;

    @TableField("created_user")
    private String createdUser;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("modify_user")
    private String modifyUser;

    @TableField("modify_time")
    private LocalDateTime modifyTime;
}
