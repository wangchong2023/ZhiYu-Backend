package com.zhiyu.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_log")
public class AppLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("level")
    private String level;

    @TableField("logger")
    private String logger;

    @TableField("message")
    private String message;

    @TableField("stack_trace")
    private String stackTrace;

    @TableField("thread_name")
    private String threadName;

    @TableField("trace_id")
    private String traceId;

    @TableField("user_id")
    private Long userId;

    @TableField("module")
    private String module;
}
