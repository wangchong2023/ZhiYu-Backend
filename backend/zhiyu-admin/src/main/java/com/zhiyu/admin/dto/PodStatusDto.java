package com.zhiyu.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PodStatusDto {
    private String name;
    private String namespace;
    private String ready;
    private String status;
    private int restarts;
    private String startTime;
    private String node;
}
