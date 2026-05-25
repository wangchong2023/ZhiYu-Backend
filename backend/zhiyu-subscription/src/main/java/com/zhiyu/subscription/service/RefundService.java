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

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final int REFUND_WINDOW_DAYS = 7;

    private final RefundRecordMapper refundRecordMapper;
    private final SubscriptionOrderMapper orderMapper;

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

        // Check for existing pending refund
        Long existingCount = refundRecordMapper.selectCount(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getOrderId, order.getId())
                        .eq(RefundRecord::getStatus, STATUS_PENDING_REVIEW));
        if (existingCount > 0) {
            throw new BizException(BizErrorCode.REFUND_IN_PROGRESS);
        }

        String refundNo = "RF" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + System.currentTimeMillis() % 1000000;

        RefundRecord record = RefundRecord.builder()
                .refundNo(refundNo)
                .orderId(order.getId())
                .userId(userId)
                .amount(order.getAmount())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(STATUS_PENDING_REVIEW)
                .appliedAt(LocalDateTime.now())
                .build();
        refundRecordMapper.insert(record);
        if (log.isInfoEnabled()) {
            log.info("Refund applied: refundNo={}, orderId={}, userId={}", refundNo, order.getId(), userId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(final Long refundId, final Long reviewerId) {
        RefundRecord record = refundRecordMapper.selectById(refundId);
        if (record == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(record.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime now = LocalDateTime.now();
        record.setStatus(STATUS_APPROVED);
        record.setReviewerId(reviewerId);
        record.setReviewedAt(now);
        record.setRefundedAt(now);
        refundRecordMapper.updateById(record);

        // Update order to REFUNDED
        SubscriptionOrder order = orderMapper.selectById(record.getOrderId());
        if (order != null) {
            order.setStatus(STATUS_REFUNDED);
            order.setUpdatedAt(now);
            orderMapper.updateById(order);
        }

        log.info("Refund approved: refundId={}, reviewerId={}", refundId, reviewerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(final Long refundId, final Long reviewerId, final String note) {
        RefundRecord record = refundRecordMapper.selectById(refundId);
        if (record == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!STATUS_PENDING_REVIEW.equals(record.getStatus())) {
            throw new BizException(BizErrorCode.ORDER_ALREADY_PAID);
        }

        LocalDateTime now = LocalDateTime.now();
        record.setStatus(STATUS_REJECTED);
        record.setReviewerId(reviewerId);
        record.setReviewNote(note);
        record.setReviewedAt(now);
        refundRecordMapper.updateById(record);

        log.info("Refund rejected: refundId={}, reviewerId={}", refundId, reviewerId);
    }
}
