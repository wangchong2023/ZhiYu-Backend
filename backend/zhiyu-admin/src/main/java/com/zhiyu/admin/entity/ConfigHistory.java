package com.zhiyu.admin.entity;

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
@TableName("config_history")
public class ConfigHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private String groupId;

    @TableField("data_id")
    private String dataId;

    @TableField("content")
    private String content;

    @TableField("format")
    private String format;

    @TableField("version")
    private Integer version;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_type")
    private String operatorType;

    @TableField("change_summary")
    private String changeSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
