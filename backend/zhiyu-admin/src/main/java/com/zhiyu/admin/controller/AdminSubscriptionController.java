package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.PaymentPageDto;
import com.zhiyu.admin.dto.RefundPageDto;
import com.zhiyu.admin.dto.RefundReviewRequest;
import com.zhiyu.admin.dto.SubscriptionPageDto;
import com.zhiyu.admin.service.AdminSubscriptionService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-订阅管理", description = "订阅、支付和退款管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    @Operation(summary = "订阅列表")
    @GetMapping("/subscriptions")
    public ApiResponse<Page<SubscriptionPageDto>> listSubscriptions(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String planKey) {
        return ApiResponse.success(adminSubscriptionService.listSubscriptions(page, size, status, planKey));
    }

    @Operation(summary = "支付记录")
    @GetMapping("/payments")
    public ApiResponse<Page<PaymentPageDto>> listPayments(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String channel,
            @RequestParam(required = false) final String status) {
        return ApiResponse.success(adminSubscriptionService.listPayments(page, size, channel, status));
    }

    @Operation(summary = "退款列表")
    @GetMapping("/refunds")
    public ApiResponse<Page<RefundPageDto>> listRefunds(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String status) {
        return ApiResponse.success(adminSubscriptionService.listRefunds(page, size, status));
    }

    @Operation(summary = "审批退款")
    @PostMapping("/refunds/{id}/approve")
    public ApiResponse<Void> approveRefund(@PathVariable final Long id,
                                            @Valid @RequestBody final RefundReviewRequest request) {
        adminSubscriptionService.approveRefund(id, null, request.getNote());
        return ApiResponse.success(null);
    }

    @Operation(summary = "拒绝退款")
    @PostMapping("/refunds/{id}/reject")
    public ApiResponse<Void> rejectRefund(@PathVariable final Long id,
                                           @Valid @RequestBody final RefundReviewRequest request) {
        adminSubscriptionService.rejectRefund(id, null, request.getNote());
        return ApiResponse.success(null);
    }
}
