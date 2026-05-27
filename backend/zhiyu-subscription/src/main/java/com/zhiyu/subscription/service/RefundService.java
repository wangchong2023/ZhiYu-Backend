/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: RefundService.java
 * 创建时间: 2026-05-27
 * 描述: 退款业务服务，提供退款申请、退款审批、退款拒绝等核心流程。
 */
package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.dto.RefundRequest;
import com.zhiyu.subscription.entity.RefundRecord;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.mapper.RefundRecordMapper;
import com.zhiyu.subscription.mapper.SubscriptionOrderMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 类名: RefundService
 * 描述: 退款业务服务，处理用户退款申请的全流程。
 *       包括申请校验、退款记录创建、审批通过后订单状态回写及拒绝流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** 订单已支付状态 */
    private static final String STATUS_PAID = "PAID";
    /** 订单已退款状态 */
    private static final String STATUS_REFUNDED = "REFUNDED";
    /** 退款待审核状态 */
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** 退款已批准状态 */
    private static final String STATUS_APPROVED = "APPROVED";
    /** 退款已拒绝状态 */
    private static final String STATUS_REJECTED = "REJECTED";
    /** 退款申请窗口期（天），超过此期限不允许退款 */
    private static final int REFUND_WINDOW_DAYS = 7;
    /** 退款单号生成用模数，取毫秒时间戳末尾6位 */
    private static final int REFUND_NO_MODULUS = 1_000_000;

    private final RefundRecordMapper refundRecordMapper;
    private final SubscriptionOrderMapper orderMapper;

    /**
     * 描述: 用户申请退款。
     *      校验订单归属、支付状态及是否存在待审核中的退款记录，
     *      通过后创建退款记录并置为待审核状态。
     *
     * @param request 退款申请请求（包含订单ID、退款原因等）
     * @param userId  当前用户ID，用于鉴权
     * @throws BizException 订单不存在、无权限、非已支付状态或退款进行中时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(final RefundRequest request, final Long userId) {
        SubscriptionOrder order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ACCESS_DENIED);
        }
        if (!STATUS_PAID.equals(order.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }

        // 检查是否存在进行中的退款申请，避免重复提交
        Long existingCount = refundRecordMapper.selectCount(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getOrderId, order.getId())
                        .eq(RefundRecord::getStatus, STATUS_PENDING_REVIEW));
        if (existingCount > 0) {
            throw new BizException(BizErrorCode.REFUND_IN_PROGRESS);
        }

        // 生成退款单号：RF + 日期 + 毫秒时间戳后6位
        String refundNo = "RF" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + System.currentTimeMillis() % REFUND_NO_MODULUS;

        RefundRecord refundRecord = RefundRecord.builder()
                .refundNo(refundNo)
                .orderId(order.getId())
                .userId(userId)
                .amount(order.getAmount())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(STATUS_PENDING_REVIEW)
                .appliedAt(LocalDateTime.now())
                .build();
        refundRecordMapper.insert(refundRecord);
        if (log.isInfoEnabled()) {
            log.info("退款申请已提交：refundNo={}, orderId={}, userId={}", refundNo, order.getId(), userId);
        }
    }

    /**
     * 描述: 审批人批准退款申请。
     *      更新退款记录状态为已批准，并将关联订单状态同步为已退款。
     *
     * @param refundId   退款记录ID
     * @param reviewerId 审批人用户ID
     * @throws BizException 退款记录不存在或非待审核状态时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(final Long refundId, final Long reviewerId) {
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(refundRecord.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime currentTime = LocalDateTime.now();
        refundRecord.setStatus(STATUS_APPROVED);
        refundRecord.setReviewerId(reviewerId);
        refundRecord.setReviewedAt(currentTime);
        refundRecord.setRefundedAt(currentTime);
        refundRecordMapper.updateById(refundRecord);

        // 退款批准后，将关联订单状态同步更新为已退款
        SubscriptionOrder order = orderMapper.selectById(refundRecord.getOrderId());
        if (order != null) {
            order.setStatus(STATUS_REFUNDED);
            order.setUpdatedAt(currentTime);
            orderMapper.updateById(order);
        }

        log.info("退款已批准：refundId={}, reviewerId={}", refundId, reviewerId);
    }

    /**
     * 描述: 审批人拒绝退款申请。
     *      更新退款记录状态为已拒绝并记录拒绝原因；订单状态保持不变。
     *
     * @param refundId   退款记录ID
     * @param reviewerId 审批人用户ID
     * @param note       拒绝原因备注
     * @throws BizException 退款记录不存在或非待审核状态时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(final Long refundId, final Long reviewerId, final String note) {
        RefundRecord refundRecord = refundRecordMapper.selectById(refundId);
        if (refundRecord == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(refundRecord.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime currentTime = LocalDateTime.now();
        refundRecord.setStatus(STATUS_REJECTED);
        refundRecord.setReviewerId(reviewerId);
        refundRecord.setReviewNote(note);
        refundRecord.setReviewedAt(currentTime);
        refundRecordMapper.updateById(refundRecord);

        log.info("退款已拒绝：refundId={}, reviewerId={}", refundId, reviewerId);
    }
}
