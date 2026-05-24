package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "账户恢复申请响应")
public class RecoveryApplyResponse {

    @Schema(description = "工单号")
    private String ticketNo;

    @Schema(description = "工单过期时间")
    private LocalDateTime expiresAt;
}
