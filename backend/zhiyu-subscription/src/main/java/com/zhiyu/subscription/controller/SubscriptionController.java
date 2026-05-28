package com.zhiyu.subscription.controller;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.subscription.dto.CreateOrderRequest;
import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.dto.RefundRequest;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.service.OrderService;
import com.zhiyu.subscription.service.RefundService;
import com.zhiyu.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订阅", description = "订阅管理、订单创建与支付、退款申请")
@RestController
@RequestMapping("/api/v1/subscriptions")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final OrderService orderService;
    private final RefundService refundService;

    @Operation(summary = "获取当前订阅", description = "查询当前登录用户的订阅状态")
    @GetMapping("/me")
    public ApiResponse<SubscriptionDto> getCurrentSubscription() {
        return ApiResponse.success(subscriptionService.getCurrentSubscription(getCurrentUserId()));
    }

    @Operation(summary = "创建订单", description = "根据套餐、周期和支付渠道创建预支付订单")
    @PostMapping("/orders")
    @com.alibaba.csp.sentinel.annotation.SentinelResource("subscription-create-order")
    public ApiResponse<OrderDto> createOrder(@Valid @RequestBody final CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(request, getCurrentUserId()));
    }

    @Operation(summary = "支付订单", description = "完成指定订单的支付，激活订阅")
    @PostMapping("/orders/{orderNo}/pay")
    @com.alibaba.csp.sentinel.annotation.SentinelResource("subscription-pay-order")
    public ApiResponse<OrderDto> payOrder(@PathVariable("orderNo") final String orderNo) {
        return ApiResponse.success(orderService.payOrder(orderNo, getCurrentUserId()));
    }

    @Operation(summary = "取消订阅", description = "取消当前订阅（到期后不再续费）")
    @PostMapping("/cancel")
    public ApiResponse<Void> cancelSubscription() {
        subscriptionService.cancelSubscription(getCurrentUserId());
        return ApiResponse.success(null);
    }

    @Operation(summary = "申请退款", description = "对已支付订单申请退款")
    @PostMapping("/refunds")
    public ApiResponse<Void> applyRefund(@Valid @RequestBody final RefundRequest request) {
        refundService.applyRefund(request, getCurrentUserId());
        return ApiResponse.success(null);
    }

    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
