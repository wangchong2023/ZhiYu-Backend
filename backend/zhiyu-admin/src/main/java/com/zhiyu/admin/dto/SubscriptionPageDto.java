package com.zhiyu.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPageDto {
    private Long id;
    private Long userId;
    private String username;
    private String planKey;
    private String planName;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer autoRenew;
    private LocalDateTime createdAt;
}
