package com.zhiyu.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundReviewRequest {
    @NotBlank
    private String decision;

    private String note;
}
