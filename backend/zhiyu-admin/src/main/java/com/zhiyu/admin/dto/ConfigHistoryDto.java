package com.zhiyu.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigHistoryDto {
    private Long id;
    private String groupId;
    private String dataId;
    private String format;
    private Integer version;
    private Long operatorId;
    private String operatorType;
    private String changeSummary;
    private LocalDateTime createdAt;
}
