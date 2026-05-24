package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role_user_relation")
public class AuthRoleUserRelation {

    @TableId(type = IdType.AUTO)
    private Integer authRoleUserRelationId;

    @TableField("auth_role_id")
    private Integer authRoleId;

    @TableField("auth_user_id")
    private Long authUserId;
}
