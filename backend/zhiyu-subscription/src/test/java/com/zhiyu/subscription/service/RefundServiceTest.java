package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.dto.RefundRequest;
import com.zhiyu.subscription.entity.RefundRecord;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.mapper.RefundRecordMapper;
import com.zhiyu.subscription.mapper.SubscriptionOrderMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRecordMapper refundRecordMapper;

    @Mock
    private SubscriptionOrderMapper orderMapper;

    @InjectMocks
    private RefundService refundService;

    @SuppressWarnings("checkstyle:MagicNumber")
    private static SubscriptionOrder buildOrder(Long id, Long userId, String status, int amount) {
        return SubscriptionOrder.builder()
                .id(id).orderNo("ZY20250101000001").userId(userId)
                .planId(2L).planKey("lite").period("MONTHLY")
                .amount(amount).currency("CNY").channel("WECHAT")
                .status(status)
                .build();
    }

    private static RefundRecord buildRefund(Long id, Long orderId, Long userId,
                                              String status) {
        return RefundRecord.builder()
                .id(id).refundNo("RF20250101000001").orderId(orderId)
                .userId(userId).amount(2900).reason("NOT_SATISFIED")
                .status(status)
                .build();
    }

    // ── applyRefund() ─────────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldApplyRefundSuccessfully() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(10L);
        request.setReason("NOT_SATISFIED");
        request.setDescription("Not satisfied with the service");

        SubscriptionOrder order = buildOrder(10L, 1001L, "PAID", 2900);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        refundService.applyRefund(request, 1001L);

        verify(refundRecordMapper).insert(any(RefundRecord.class));
    }

    @Test
    void shouldThrowOrderNotFoundWhenRefundingNonExistentOrder() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(9999L);
        request.setReason("NOT_SATISFIED");
        when(orderMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> refundService.applyRefund(request, 1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ORDER_NOT_FOUND.getCode());

        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldThrowAccessDeniedWhenRefundingOtherUserOrder() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(10L);
        request.setReason("NOT_SATISFIED");

        SubscriptionOrder order = buildOrder(10L, 2002L, "PAID", 2900);
        when(orderMapper.selectById(10L)).thenReturn(order);

        assertThatThrownBy(() -> refundService.applyRefund(request, 1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ACCESS_DENIED.getCode());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldThrowRefundInProgressWhenDuplicateRefund() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(10L);
        request.setReason("NOT_SATISFIED");

        SubscriptionOrder order = buildOrder(10L, 1001L, "PAID", 2900);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> refundService.applyRefund(request, 1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.REFUND_IN_PROGRESS.getCode());
    }

    // ── approveRefund() ───────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldApproveRefundSuccessfully() {
        RefundRecord record = buildRefund(1L, 10L, 1001L, "PENDING_REVIEW");
        SubscriptionOrder order = buildOrder(10L, 1001L, "PAID", 2900);
        when(refundRecordMapper.selectById(1L)).thenReturn(record);
        when(orderMapper.selectById(10L)).thenReturn(order);

        refundService.approveRefund(1L, 2001L);

        assertThat(record.getStatus()).isEqualTo("APPROVED");
        assertThat(record.getReviewerId()).isEqualTo(2001L);
        assertThat(record.getReviewedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        verify(refundRecordMapper).updateById(record);
        verify(orderMapper).updateById(order);
    }

    @Test
    void shouldThrowResourceNotFoundWhenApprovingNonExistentRefund() {
        when(refundRecordMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> refundService.approveRefund(9999L, 2001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldThrowWhenApprovingNonPendingRefund() {
        RefundRecord record = buildRefund(1L, 10L, 1001L, "APPROVED");
        when(refundRecordMapper.selectById(1L)).thenReturn(record);

        assertThatThrownBy(() -> refundService.approveRefund(1L, 2001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ORDER_ALREADY_PAID.getCode());
    }

    // ── rejectRefund() ────────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldRejectRefundSuccessfully() {
        RefundRecord record = buildRefund(1L, 10L, 1001L, "PENDING_REVIEW");
        when(refundRecordMapper.selectById(1L)).thenReturn(record);

        refundService.rejectRefund(1L, 2001L, "Not eligible");

        assertThat(record.getStatus()).isEqualTo("REJECTED");
        assertThat(record.getReviewerId()).isEqualTo(2001L);
        assertThat(record.getReviewNote()).isEqualTo("Not eligible");
        assertThat(record.getReviewedAt()).isNotNull();
        verify(refundRecordMapper).updateById(record);
    }

    @Test
    void shouldThrowWhenRejectingNonPendingRefund() {
        RefundRecord record = buildRefund(1L, 10L, 1001L, "REJECTED");
        when(refundRecordMapper.selectById(1L)).thenReturn(record);

        assertThatThrownBy(() -> refundService.rejectRefund(1L, 2001L, "Already rejected"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ORDER_ALREADY_PAID.getCode());
    }
}
