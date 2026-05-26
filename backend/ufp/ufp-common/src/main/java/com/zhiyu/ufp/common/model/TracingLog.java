package com.zhiyu.ufp.common.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class TracingLog implements Serializable {

    private String appName;
    private String module;
    private String className;
    private String methodName;
    private String operation;
    private String description;
    private String ipAddress;
    private String requestUri;
    private String requestParams;
    private String response;
    private long duration;
    private String status;
    private String errorMessage;
    private long timestamp;
}
