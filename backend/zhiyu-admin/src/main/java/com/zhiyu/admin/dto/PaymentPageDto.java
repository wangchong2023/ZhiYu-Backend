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
public class PaymentPageDto {
    private Long id;
    private Long orderId;
    private Long userId;
    private String username;
    private String channel;
    private String transactionId;
    private Integer amount;
    private String currency;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
