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
@TableName("auth_role")
public class AuthRole {

    @TableId(type = IdType.AUTO)
    private Integer authRoleId;

    @TableField("auth_role_name")
    private String authRoleName;

    @TableField("auth_role_code")
    private String authRoleCode;

    @TableField("auth_role_enable")
    private Integer authRoleEnable;

    @TableField("auth_role_desc")
    private String authRoleDesc;

    @TableField("created_user")
    private String createdUser;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("modify_user")
    private String modifyUser;

    @TableField("modify_time")
    private LocalDateTime modifyTime;
}
