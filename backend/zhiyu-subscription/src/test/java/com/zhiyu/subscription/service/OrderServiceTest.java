package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.dto.CreateOrderRequest;
import com.zhiyu.subscription.dto.OrderDto;
import com.zhiyu.subscription.entity.PaymentRecord;
import com.zhiyu.subscription.entity.SubscriptionOrder;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.mapper.PaymentRecordMapper;
import com.zhiyu.subscription.mapper.SubscriptionOrderMapper;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private SubscriptionOrderMapper orderMapper;

    @Mock
    private SubscriptionPlanMapper planMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private static SubscriptionPlan buildPlan(Long id, String planKey, int priceMonthly,
                                               int priceYearly) {
        return SubscriptionPlan.builder()
                .id(id).planKey(planKey).name(planKey)
                .priceMonthly(priceMonthly).priceYearly(priceYearly)
                .isActive(1).sortOrder(0)
                .build();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private SubscriptionOrder buildOrder(Long id, String orderNo, Long userId, Long planId,
                                          String status, int amount) {
        return SubscriptionOrder.builder()
                .id(id).orderNo(orderNo).userId(userId).planId(planId)
                .planKey("lite").period("MONTHLY").amount(amount)
                .currency("CNY").channel("WECHAT").status(status)
                .build();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldCreateOrderForActivePlan() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPlanId(2L);
        request.setPeriod("MONTHLY");
        request.setChannel("WECHAT");

        SubscriptionPlan plan = buildPlan(2L, "lite", 2900, 29000);
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        OrderDto result = orderService.createOrder(request, 1001L);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1001L);
        assertThat(result.getPlanKey()).isEqualTo("lite");
        assertThat(result.getPeriod()).isEqualTo("MONTHLY");
        assertThat(result.getAmount()).isEqualTo(2900);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(orderMapper).insert(any(SubscriptionOrder.class));
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldCreateYearlyOrderWithYearlyPrice() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPlanId(2L);
        request.setPeriod("YEARLY");
        request.setChannel("ALIPAY");

        SubscriptionPlan plan = buildPlan(2L, "lite", 2900, 29000);
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        OrderDto result = orderService.createOrder(request, 1001L);

        assertThat(result.getAmount()).isEqualTo(29000);
        assertThat(result.getPeriod()).isEqualTo("YEARLY");
    }

    @Test
    void shouldThrowPlanNotExistWhenCreatingOrderForInactivePlan() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPlanId(2L);
        request.setPeriod("MONTHLY");
        request.setChannel("WECHAT");
        when(planMapper.selectById(2L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(request, 1001L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());

        verify(orderMapper, never()).insert(any(SubscriptionOrder.class));
    }

    @Test
    void shouldThrowPlanNotExistWhenPlanIsInactive() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPlanId(2L);
        request.setPeriod("MONTHLY");
        request.setChannel("WECHAT");

        SubscriptionPlan plan = buildPlan(2L, "lite", 2900, 29000);
        plan.setIsActive(0);
        when(planMapper.selectById(2L)).thenReturn(plan);

        assertThatThrownBy(() -> orderService.createOrder(request, 1001L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldPayOrderSuccessfully() {
        SubscriptionOrder order = buildOrder(10L, "ZY20250101000001", 1001L, 2L, "PENDING", 2900);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderDto result = orderService.payOrder("ZY20250101000001", 1001L);

        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getTransactionId()).isNotNull();
        verify(orderMapper).updateById(order);
        verify(paymentRecordMapper).insert(any(PaymentRecord.class));
        verify(subscriptionService).activateSubscription(eq(1001L), eq(2L), eq("MONTHLY"));
    }

    @Test
    void shouldThrowOrderNotFoundWhenPayingNonExistentOrder() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> orderService.payOrder("NONEXIST", 1001L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.ORDER_NOT_FOUND.getCode());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldThrowAccessDeniedWhenPayingOtherUserOrder() {
        SubscriptionOrder order = buildOrder(10L, "ZY20250101000001", 2002L, 2L, "PENDING", 2900);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThatThrownBy(() -> orderService.payOrder("ZY20250101000001", 1001L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.ACCESS_DENIED.getCode());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldThrowAlreadyPaidWhenPayingPaidOrder() {
        SubscriptionOrder order = buildOrder(10L, "ZY20250101000001", 1001L, 2L, "PAID", 2900);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThatThrownBy(() -> orderService.payOrder("ZY20250101000001", 1001L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.ORDER_ALREADY_PAID.getCode());
    }
}
