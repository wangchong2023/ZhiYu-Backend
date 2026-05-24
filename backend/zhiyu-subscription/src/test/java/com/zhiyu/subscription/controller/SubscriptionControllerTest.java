package com.zhiyu.subscription.controller;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.subscription.dto.CreateOrderRequest;
import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.dto.RefundRequest;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.service.OrderService;
import com.zhiyu.subscription.service.RefundService;
import com.zhiyu.subscription.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private OrderService orderService;

    @Mock
    private RefundService refundService;

    private SubscriptionController controller;

    private MockedStatic<SecurityContextHolder> mockHolder;
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new SubscriptionController(subscriptionService, orderService, refundService);

        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        mockHolder = mockStatic(SecurityContextHolder.class);
        mockHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        mockHolder.close();
    }

    // ── getCurrentSubscription() ─────────────────────────────────

    @Test
    void shouldReturnCurrentSubscription() {
        SubscriptionDto expected = SubscriptionDto.builder()
                .id(10L)
                .userId(1001L)
                .planId(1L)
                .planName("Free")
                .status("ACTIVE")
                .build();
        when(subscriptionService.getCurrentSubscription(1001L)).thenReturn(expected);

        ApiResponse<SubscriptionDto> response = controller.getCurrentSubscription();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getUserId()).isEqualTo(1001L);
        assertThat(response.getData().getPlanName()).isEqualTo("Free");
        verify(subscriptionService).getCurrentSubscription(1001L);
    }

    // ── createOrder() ────────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldCreateOrderSuccessfully() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPlanId(2L);
        request.setPeriod("MONTHLY");
        request.setChannel("WECHAT");

        OrderDto expected = OrderDto.builder()
                .id(10L)
                .orderNo("ZY20250101000001")
                .userId(1001L)
                .planId(2L)
                .planKey("lite")
                .amount(2900)
                .status("PENDING")
                .build();
        when(orderService.createOrder(request, 1001L)).thenReturn(expected);

        ApiResponse<OrderDto> response = controller.createOrder(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getOrderNo()).isEqualTo("ZY20250101000001");
        assertThat(response.getData().getAmount()).isEqualTo(2900);
        verify(orderService).createOrder(request, 1001L);
    }

    // ── payOrder() ───────────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldPayOrderSuccessfully() {
        OrderDto expected = OrderDto.builder()
                .id(10L)
                .orderNo("ZY20250101000001")
                .userId(1001L)
                .status("PAID")
                .build();
        when(orderService.payOrder("ZY20250101000001", 1001L)).thenReturn(expected);

        ApiResponse<OrderDto> response = controller.payOrder("ZY20250101000001");

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo("PAID");
        verify(orderService).payOrder("ZY20250101000001", 1001L);
    }

    // ── cancelSubscription() ─────────────────────────────────────

    @Test
    void shouldCancelSubscriptionSuccessfully() {
        ApiResponse<Void> response = controller.cancelSubscription();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();
        verify(subscriptionService).cancelSubscription(1001L);
    }

    // ── applyRefund() ─────────────────────────────────────────────

    @Test
    void shouldApplyRefundSuccessfully() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(10L);
        request.setReason("NOT_SATISFIED");
        request.setDescription("Not satisfied");

        ApiResponse<Void> response = controller.applyRefund(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        verify(refundService).applyRefund(request, 1001L);
    }

    // ── getCurrentUserId() ────────────────────────────────────────

    @Test
    void shouldUseCorrectUserIdFromSecurityContext() {
        SubscriptionDto expected = SubscriptionDto.builder()
                .userId(1001L)
                .build();
        when(subscriptionService.getCurrentSubscription(1001L)).thenReturn(expected);

        controller.getCurrentSubscription();

        verify(subscriptionService).getCurrentSubscription(1001L);
    }

    @Test
    void shouldHandleDifferentUserIdFromSecurityContext() {
        when(authentication.getPrincipal()).thenReturn("2002");

        SubscriptionDto expected = SubscriptionDto.builder()
                .userId(2002L)
                .build();
        when(subscriptionService.getCurrentSubscription(2002L)).thenReturn(expected);

        ApiResponse<SubscriptionDto> response = controller.getCurrentSubscription();

        assertThat(response.getData().getUserId()).isEqualTo(2002L);
        verify(subscriptionService).getCurrentSubscription(2002L);
    }
}
