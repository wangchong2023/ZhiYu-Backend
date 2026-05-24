package com.zhiyu.ufp.auth.entity;

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
@TableName("user_totp")
public class UserTotp {

    @TableId("user_id")
    private Long userId;

    @TableField("secret")
    private String secret;

    @TableField("enabled")
    private Integer enabled;

    @TableField("recovery_codes")
    private String recoveryCodes;
}
