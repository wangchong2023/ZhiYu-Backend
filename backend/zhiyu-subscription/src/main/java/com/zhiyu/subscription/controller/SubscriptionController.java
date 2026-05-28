/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

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

/**
 * 智宇平台订阅控制层。
 *
 * <p>主要负责处理订阅生命周期管理、预支付订单创建、订单状态变更与支付确认、
 * 订阅主动取消以及售后退款申请等核心流程的 HTTP 请求交互。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Tag(name = "订阅", description = "订阅管理、订单创建与支付、退款申请")
@RestController
@RequestMapping("/api/v1/subscriptions")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final OrderService orderService;
    private final RefundService refundService;

    /**
     * 获取当前登录用户的订阅详情。
     *
     * @return 统一 API 响应体，内部包含当前用户的订阅详情 DTO
     */
    @Operation(summary = "获取当前订阅", description = "查询当前登录用户的订阅状态")
    @GetMapping("/me")
    public ApiResponse<SubscriptionDto> getCurrentSubscription() {
        // 调用订阅服务，提取当前用户的最新订阅详情
        return ApiResponse.success(subscriptionService.getCurrentSubscription(getCurrentUserId()));
    }

    /**
     * 创建订阅预支付订单。
     *
     * <p>结合指定的套餐 ID、订阅周期（按月/按年）以及所选的支付通道，计算最终金额并落库生成预支付订单。</p>
     *
     * @param request 包含套餐、周期和渠道信息的订单创建请求体
     * @return 统一 API 响应体，内部包含新生成的订单数据 DTO
     */
    @Operation(summary = "创建订单", description = "根据套餐、周期和支付渠道创建预支付订单")
    @PostMapping("/orders")
    @com.alibaba.csp.sentinel.annotation.SentinelResource("subscription-create-order")
    public ApiResponse<OrderDto> createOrder(@Valid @RequestBody final CreateOrderRequest request) {
        // 执行创建订单核心事务
        return ApiResponse.success(orderService.createOrder(request, getCurrentUserId()));
    }

    /**
     * 对预支付订单执行模拟支付确认。
     *
     * <p>模拟并接收外部支付渠道回调/前端手动支付触发，校验订单所有权并流转订单为已支付状态，同时激活/延长对应订阅。</p>
     *
     * @param orderNo 待支付的订单流水号
     * @return 统一 API 响应体，包含流转更新后的订单数据 DTO
     */
    @Operation(summary = "支付订单", description = "完成指定订单的支付，激活订阅")
    @PostMapping("/orders/{orderNo}/pay")
    @com.alibaba.csp.sentinel.annotation.SentinelResource("subscription-pay-order")
    public ApiResponse<OrderDto> payOrder(@PathVariable("orderNo") final String orderNo) {
        // 执行支付确认及订阅流转激活的核心事务
        return ApiResponse.success(orderService.payOrder(orderNo, getCurrentUserId()));
    }

    /**
     * 主动取消当前生效的订阅。
     *
     * <p>将当前有效订阅修改为“到期不续费”状态。在本次计费周期届满前，用户仍享有对应订阅的平台权益。</p>
     *
     * @return 统一 API 响应体，操作成功时数据域为 null
     */
    @Operation(summary = "取消订阅", description = "取消当前订阅（到期后不再续费）")
    @PostMapping("/cancel")
    public ApiResponse<Void> cancelSubscription() {
        // 执行取消订阅及状态机流转
        subscriptionService.cancelSubscription(getCurrentUserId());
        return ApiResponse.success(null);
    }

    /**
     * 针对已支付订单申请退款。
     *
     * <p>校验订单退款边界条件，向底层记录退款事务记录并修改订阅生命周期状态。</p>
     *
     * @param request 包含订单号及退款缘由的退款申请体
     * @return 统一 API 响应体，操作成功时数据域为 null
     */
    @Operation(summary = "申请退款", description = "对已支付订单申请退款")
    @PostMapping("/refunds")
    public ApiResponse<Void> applyRefund(@Valid @RequestBody final RefundRequest request) {
        // 执行退款申请与退款合规性校验
        refundService.applyRefund(request, getCurrentUserId());
        return ApiResponse.success(null);
    }

    /**
     * 从 Spring Security 认证上下文中提取当前登录用户的 ID。
     *
     * @return 当前登录的认证用户主键 ID
     */
    private Long getCurrentUserId() {
        // 从 Security 上下文解析 Principal 属性，转换为 Long 类型用户 ID
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
