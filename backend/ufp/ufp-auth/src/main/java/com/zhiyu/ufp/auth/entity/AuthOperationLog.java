package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_operation_log")
public class AuthOperationLog {
    @TableId(type = IdType.INPUT)
    @TableField("log_id")
    private String logId;

    @TableField("log_time")
    private LocalDateTime logTime;

    @TableField("start_time")
    private Long startTime;

    @TableField("end_time")
    private Long endTime;

    @TableField("app_name")
    private String appName;

    @TableField("module_name")
    private String moduleName;

    @TableField("class_name")
    private String className;

    @TableField("method_name")
    private String methodName;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_desc")
    private String operationDesc;

    @TableField("method")
    private String method;

    @TableField("uri")
    private String uri;

    @TableField("remote_ip")
    private String remoteIp;

    @TableField("local_ip")
    private String localIp;

    @TableField("duration")
    private Long duration;

    @TableField("status")
    private String status;

    @TableField("status_name")
    private String statusName;

    @TableField("error")
    private String error;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("loc_country")
    private String locCountry;

    @TableField("loc_prov")
    private String locProv;

    @TableField("loc_city")
    private String locCity;

    @TableField("loc_isp")
    private String locIsp;
}
