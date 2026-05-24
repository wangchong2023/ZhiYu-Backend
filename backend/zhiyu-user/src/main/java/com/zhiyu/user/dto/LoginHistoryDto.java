package com.zhiyu.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户登录历史行")
public class LoginHistoryDto {
    @Schema(description = "ID") private Long id;
    @Schema(description = "用户名") private String username;
    @Schema(description = "操作类型") private String action;
    @Schema(description = "登录方式") private String type;
    @Schema(description = "结果") private String result;
    @Schema(description = "IP地址") private String ip;
    @Schema(description = "设备信息") private String device;
    @Schema(description = "位置") private String location;
    @Schema(description = "时间") private LocalDateTime time;
}
