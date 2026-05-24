package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.PaymentPageDto;
import com.zhiyu.admin.dto.RefundPageDto;
import com.zhiyu.admin.dto.SubscriptionPageDto;
import com.zhiyu.subscription.entity.PaymentRecord;
import com.zhiyu.subscription.entity.RefundRecord;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.entity.UserSubscription;
import com.zhiyu.subscription.mapper.PaymentRecordMapper;
import com.zhiyu.subscription.mapper.RefundRecordMapper;
import com.zhiyu.subscription.mapper.SubscriptionOrderMapper;
import com.zhiyu.subscription.mapper.UserSubscriptionMapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private static final int ERR_REFUND_NOT_FOUND = 40401;
    private static final int ERR_REFUND_NOT_PENDING = 40001;

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final SubscriptionOrderMapper subscriptionOrderMapper;
    private final AuthUserMapper authUserMapper;

    public Page<SubscriptionPageDto> listSubscriptions(final int page, final int size,
                                                        final String status, final String planKey) {
        var wrapper = new LambdaQueryWrapper<UserSubscription>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(UserSubscription::getStatus, status);
        }
        wrapper.orderByDesc(UserSubscription::getCreatedAt);

        Page<UserSubscription> entityPage = userSubscriptionMapper.selectPage(new Page<>(page, size), wrapper);
        Page<SubscriptionPageDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(s -> {
                    AuthUser user = authUserMapper.selectById(s.getUserId());
                    return SubscriptionPageDto.builder()
                            .id(s.getId())
                            .userId(s.getUserId())
                            .username(user != null ? user.getAuthUserUsername() : null)
                            .planKey(planKey)
                            .status(s.getStatus())
                            .startDate(s.getStartDate())
                            .endDate(s.getEndDate())
                            .autoRenew(s.getAutoRenew())
                            .createdAt(s.getCreatedAt())
                            .build();
                })
                .toList());
        return dtoPage;
    }

    public Page<PaymentPageDto> listPayments(final int page, final int size,
                                              final String channel, final String status) {
        var wrapper = new LambdaQueryWrapper<PaymentRecord>();
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(PaymentRecord::getChannel, channel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PaymentRecord::getStatus, status);
        }
        wrapper.orderByDesc(PaymentRecord::getCreatedAt);

        Page<PaymentRecord> entityPage = paymentRecordMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PaymentPageDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(p -> {
                    AuthUser user = authUserMapper.selectById(p.getUserId());
                    return PaymentPageDto.builder()
                            .id(p.getId())
                            .orderId(p.getOrderId())
                            .userId(p.getUserId())
                            .username(user != null ? user.getAuthUserUsername() : null)
                            .channel(p.getChannel())
                            .transactionId(p.getTransactionId())
                            .amount(p.getAmount())
                            .currency(p.getCurrency())
                            .status(p.getStatus())
                            .paidAt(p.getPaidAt())
                            .createdAt(p.getCreatedAt())
                            .build();
                })
                .toList());
        return dtoPage;
    }

    public Page<RefundPageDto> listRefunds(final int page, final int size, final String status) {
        var wrapper = new LambdaQueryWrapper<RefundRecord>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(RefundRecord::getStatus, status);
        }
        wrapper.orderByDesc(RefundRecord::getAppliedAt);

        Page<RefundRecord> entityPage = refundRecordMapper.selectPage(new Page<>(page, size), wrapper);
        Page<RefundPageDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(r -> {
                    AuthUser user = authUserMapper.selectById(r.getUserId());
                    SubscriptionOrder order = subscriptionOrderMapper.selectById(r.getOrderId());
                    return RefundPageDto.builder()
                            .id(r.getId())
                            .refundNo(r.getRefundNo())
                            .userId(r.getUserId())
                            .username(user != null ? user.getAuthUserUsername() : null)
                            .orderId(r.getOrderId())
                            .orderNo(order != null ? order.getOrderNo() : null)
                            .amount(r.getAmount())
                            .reason(r.getReason())
                            .status(r.getStatus())
                            .reviewerId(r.getReviewerId())
                            .reviewNote(r.getReviewNote())
                            .appliedAt(r.getAppliedAt())
                            .reviewedAt(r.getReviewedAt())
                            .build();
                })
                .toList());
        return dtoPage;
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(final Long refundId, final Long reviewerId, final String note) {
        RefundRecord refund = refundRecordMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ERR_REFUND_NOT_FOUND, "退款单不存在");
        }
        if (!"PENDING_REVIEW".equals(refund.getStatus())) {
            throw new BizException(ERR_REFUND_NOT_PENDING, "退款单状态不允许审核");
        }
        refund.setStatus("APPROVED");
        refund.setReviewerId(reviewerId);
        refund.setReviewNote(note);
        refund.setReviewedAt(LocalDateTime.now());
        refundRecordMapper.updateById(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(final Long refundId, final Long reviewerId, final String note) {
        RefundRecord refund = refundRecordMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ERR_REFUND_NOT_FOUND, "退款单不存在");
        }
        if (!"PENDING_REVIEW".equals(refund.getStatus())) {
            throw new BizException(ERR_REFUND_NOT_PENDING, "退款单状态不允许审核");
        }
        refund.setStatus("REJECTED");
        refund.setReviewerId(reviewerId);
        refund.setReviewNote(note);
        refund.setReviewedAt(LocalDateTime.now());
        refundRecordMapper.updateById(refund);
    }
}
