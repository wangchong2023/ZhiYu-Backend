package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "恢复工单审核请求")
public class RecoveryReviewRequest {

    @NotBlank
    @Schema(description = "审核决定: APPROVED | REJECTED", example = "APPROVED")
    private String decision;

    @Schema(description = "审核备注")
    private String note;
}
