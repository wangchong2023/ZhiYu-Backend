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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSubscriptionServiceTest {

    @Mock private UserSubscriptionMapper userSubscriptionMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private SubscriptionOrderMapper subscriptionOrderMapper;
    @Mock private AuthUserMapper authUserMapper;
    @InjectMocks private AdminSubscriptionService adminSubscriptionService;

    @Test
    void shouldListSubscriptions() {
        UserSubscription sub = UserSubscription.builder()
                .id(1L).userId(1L).status("ACTIVE").build();
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("testuser").build();
        when(userSubscriptionMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<UserSubscription>(1, 10, 1).setRecords(List.of(sub)));
        when(authUserMapper.selectById(1L)).thenReturn(user);

        Page<SubscriptionPageDto> result = adminSubscriptionService.listSubscriptions(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldListPaymentsWithFilters() {
        PaymentRecord payment = PaymentRecord.builder()
                .id(1L).userId(1L).channel("WECHAT").status("PAID")
                .amount(2990).createdAt(LocalDateTime.now()).build();
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("testuser").build();
        when(paymentRecordMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<PaymentRecord>(1, 10, 1).setRecords(List.of(payment)));
        when(authUserMapper.selectById(1L)).thenReturn(user);

        Page<PaymentPageDto> result = adminSubscriptionService.listPayments(1, 10, "WECHAT", null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getChannel()).isEqualTo("WECHAT");
    }

    @Test
    void shouldListRefunds() {
        RefundRecord refund = RefundRecord.builder()
                .id(1L).refundNo("RF001").userId(1L).orderId(1L)
                .amount(2990).status("PENDING_REVIEW").build();
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("testuser").build();
        SubscriptionOrder order = SubscriptionOrder.builder()
                .id(1L).orderNo("ORD001").build();
        when(refundRecordMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<RefundRecord>(1, 10, 1).setRecords(List.of(refund)));
        when(authUserMapper.selectById(1L)).thenReturn(user);
        when(subscriptionOrderMapper.selectById(1L)).thenReturn(order);

        Page<RefundPageDto> result = adminSubscriptionService.listRefunds(1, 10, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getOrderNo()).isEqualTo("ORD001");
    }

    @Test
    void shouldApproveRefund() {
        RefundRecord refund = RefundRecord.builder()
                .id(1L).status("PENDING_REVIEW").build();
        when(refundRecordMapper.selectById(1L)).thenReturn(refund);

        adminSubscriptionService.approveRefund(1L, 100L, "Approved");

        assertThat(refund.getStatus()).isEqualTo("APPROVED");
        verify(refundRecordMapper).updateById(refund);
    }

    @Test
    void shouldRejectRefund() {
        RefundRecord refund = RefundRecord.builder()
                .id(1L).status("PENDING_REVIEW").build();
        when(refundRecordMapper.selectById(1L)).thenReturn(refund);

        adminSubscriptionService.rejectRefund(1L, 100L, "Invalid");

        assertThat(refund.getStatus()).isEqualTo("REJECTED");
        verify(refundRecordMapper).updateById(refund);
    }

    @Test
    void shouldThrowWhenRefundNotPending() {
        RefundRecord refund = RefundRecord.builder()
                .id(1L).status("APPROVED").build();
        when(refundRecordMapper.selectById(1L)).thenReturn(refund);

        assertThatThrownBy(() -> adminSubscriptionService.approveRefund(1L, 100L, "note"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldThrowWhenRefundNotFound() {
        when(refundRecordMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminSubscriptionService.approveRefund(99L, 100L, "note"))
                .isInstanceOf(BizException.class);
    }
}
