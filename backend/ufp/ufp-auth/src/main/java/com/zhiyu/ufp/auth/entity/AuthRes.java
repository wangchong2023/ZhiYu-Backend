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
@TableName("auth_res")
public class AuthRes {

    @TableId(type = IdType.AUTO)
    private Integer authResId;

    @TableField("auth_res_pid")
    private Integer authResPid;

    @TableField("auth_res_name")
    private String authResName;

    @TableField("auth_res_type")
    private Integer authResType;

    @TableField("auth_res_code")
    private String authResCode;

    @TableField("auth_res_index")
    private Integer authResIndex;

    @TableField("auth_res_enable")
    private Integer authResEnable;

    @TableField("auth_res_desc")
    private String authResDesc;
}
