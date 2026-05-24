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
public class RefundPageDto {
    private Long id;
    private String refundNo;
    private Long userId;
    private String username;
    private Long orderId;
    private String orderNo;
    private Integer amount;
    private String reason;
    private String status;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime appliedAt;
    private LocalDateTime reviewedAt;
}
