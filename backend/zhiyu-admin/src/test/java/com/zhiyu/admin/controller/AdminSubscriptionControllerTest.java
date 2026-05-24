package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.PaymentPageDto;
import com.zhiyu.admin.dto.RefundPageDto;
import com.zhiyu.admin.dto.RefundReviewRequest;
import com.zhiyu.admin.dto.SubscriptionPageDto;
import com.zhiyu.admin.service.AdminSubscriptionService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSubscriptionControllerTest {

    @Mock private AdminSubscriptionService adminSubscriptionService;
    @InjectMocks private AdminSubscriptionController adminSubscriptionController;

    @Test
    void shouldListSubscriptions() {
        Page<SubscriptionPageDto> page = new Page<>(1, 20, 0);
        when(adminSubscriptionService.listSubscriptions(1, 20, null, null)).thenReturn(page);

        ApiResponse<Page<SubscriptionPageDto>> resp = adminSubscriptionController.listSubscriptions(1, 20, null, null);

        assertThat(resp.getData()).isNotNull();
    }

    @Test
    void shouldListPayments() {
        Page<PaymentPageDto> page = new Page<>(1, 20, 0);
        when(adminSubscriptionService.listPayments(1, 20, "WECHAT", "PAID")).thenReturn(page);

        ApiResponse<Page<PaymentPageDto>> resp = adminSubscriptionController.listPayments(1, 20, "WECHAT", "PAID");

        assertThat(resp.getData()).isNotNull();
    }

    @Test
    void shouldListRefunds() {
        Page<RefundPageDto> page = new Page<>(1, 20, 0);
        when(adminSubscriptionService.listRefunds(1, 20, "PENDING_REVIEW")).thenReturn(page);

        ApiResponse<Page<RefundPageDto>> resp = adminSubscriptionController.listRefunds(1, 20, "PENDING_REVIEW");

        assertThat(resp.getData()).isNotNull();
    }

    @Test
    void shouldApproveRefund() {
        RefundReviewRequest req = new RefundReviewRequest();
        req.setDecision("APPROVED");
        req.setNote("Valid");

        ApiResponse<Void> resp = adminSubscriptionController.approveRefund(1L, req);

        verify(adminSubscriptionService).approveRefund(eq(1L), isNull(), eq("Valid"));
        assertThat(resp.getCode()).isZero();
    }

    @Test
    void shouldRejectRefund() {
        RefundReviewRequest req = new RefundReviewRequest();
        req.setDecision("REJECTED");
        req.setNote("Invalid reason");

        ApiResponse<Void> resp = adminSubscriptionController.rejectRefund(1L, req);

        verify(adminSubscriptionService).rejectRefund(eq(1L), isNull(), eq("Invalid reason"));
        assertThat(resp.getCode()).isZero();
    }
}
