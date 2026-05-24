package com.zhiyu.notification.entity;

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
@TableName("notification_template")
public class NotificationTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_key")
    private String templateKey;

    private String type;

    private String subject;

    private String body;

    @TableField("variables_json")
    private String variablesJson;

    private String description;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
