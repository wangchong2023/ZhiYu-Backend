package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.entity.QuotaUsage;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.entity.UserSubscription;
import com.zhiyu.subscription.mapper.QuotaUsageMapper;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.subscription.mapper.UserSubscriptionMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserSubscriptionMapper subscriptionMapper;

    @Mock
    private SubscriptionPlanMapper planMapper;

    @Mock
    private QuotaUsageMapper quotaUsageMapper;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static SubscriptionPlan buildPlan(Long id, String planKey, int isActive) {
        return SubscriptionPlan.builder()
                .id(id).planKey(planKey).name(planKey)
                .priceMonthly(0).priceYearly(0).isActive(isActive).sortOrder(0)
                .build();
    }

    private static UserSubscription buildSubscription(Long id, Long userId, Long planId,
                                                       String status, int trialUsed) {
        return UserSubscription.builder()
                .id(id).userId(userId).planId(planId).status(status)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(1))
                .autoRenew(0).trialUsed(trialUsed)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // ── activateDefaultPlan() ─────────────────────────────────────

    @Test
    void shouldActivateFreePlanForNewUser() {
        SubscriptionPlan freePlan = buildPlan(1L, "free", 1);
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(freePlan);

        subscriptionService.activateDefaultPlan(1001L);

        verify(subscriptionMapper).insert(any(UserSubscription.class));
    }

    @Test
    void shouldThrowPlanNotExistWhenFreePlanNotFound() {
        when(planMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> subscriptionService.activateDefaultPlan(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());

        verify(subscriptionMapper, never()).insert(any(UserSubscription.class));
    }

    // ── getCurrentSubscription() ─────────────────────────────────

    @Test
    void shouldReturnCurrentSubscriptionWhenExists() {
        UserSubscription sub = buildSubscription(10L, 1001L, 1L, "ACTIVE", 0);
        SubscriptionPlan plan = buildPlan(1L, "free", 1);
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);
        when(planMapper.selectById(1L)).thenReturn(plan);

        SubscriptionDto result = subscriptionService.getCurrentSubscription(1001L);

        assertThat(result.getUserId()).isEqualTo(1001L);
        assertThat(result.getPlanId()).isEqualTo(1L);
        assertThat(result.getPlanName()).isEqualTo("free");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldThrowResourceNotFoundWhenNoSubscription() {
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> subscriptionService.getCurrentSubscription(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldNotSetPlanNameWhenPlanIsNull() {
        UserSubscription sub = buildSubscription(10L, 1001L, 1L, "ACTIVE", 0);
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);
        when(planMapper.selectById(1L)).thenReturn(null);

        SubscriptionDto result = subscriptionService.getCurrentSubscription(1001L);

        assertThat(result.getPlanName()).isNull();
    }

    // ── checkQuota() ──────────────────────────────────────────────

    @Test
    void shouldCreateNewQuotaRecordWhenFirstUse() {
        when(quotaUsageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        subscriptionService.checkQuota(1001L, "daily_chat", 10);

        verify(quotaUsageMapper).insert(any(QuotaUsage.class));
    }

    @Test
    void shouldThrowDailyQuotaExhaustedWhenExceeded() {
        QuotaUsage existing = QuotaUsage.builder()
                .id(1L).userId(1001L).quotaKey("daily_chat")
                .usedCount(10L).version(0)
                .periodStart(LocalDate.now()).periodEnd(LocalDate.now())
                .build();
        when(quotaUsageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> subscriptionService.checkQuota(1001L, "daily_chat", 10))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.DAILY_QUOTA_EXHAUSTED.getCode());
    }

    @Test
    void shouldIncrementQuotaWhenUnderLimit() {
        QuotaUsage existing = QuotaUsage.builder()
                .id(1L).userId(1001L).quotaKey("daily_chat")
                .usedCount(5L).version(0)
                .periodStart(LocalDate.now()).periodEnd(LocalDate.now())
                .build();
        when(quotaUsageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(quotaUsageMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(1);

        subscriptionService.checkQuota(1001L, "daily_chat", 10);

        verify(quotaUsageMapper).update(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldThrowWhenOptimisticLockFails() {
        QuotaUsage existing = QuotaUsage.builder()
                .id(1L).userId(1001L).quotaKey("daily_chat")
                .usedCount(5L).version(0)
                .periodStart(LocalDate.now()).periodEnd(LocalDate.now())
                .build();
        when(quotaUsageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(quotaUsageMapper.update(any(), any(LambdaQueryWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> subscriptionService.checkQuota(1001L, "daily_chat", 10))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.DAILY_QUOTA_EXHAUSTED.getCode());
    }

    // ── cancelSubscription() ─────────────────────────────────────

    @Test
    void shouldCancelSubscriptionSuccessfully() {
        UserSubscription sub = buildSubscription(10L, 1001L, 1L, "ACTIVE", 0);
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);

        subscriptionService.cancelSubscription(1001L);

        assertThat(sub.getStatus()).isEqualTo("CANCELING");
        assertThat(sub.getCancelledAt()).isNotNull();
        verify(subscriptionMapper).updateById(sub);
    }

    @Test
    void shouldThrowResourceNotFoundWhenCancelWithoutSubscription() {
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(subscriptionMapper, never()).updateById(any(UserSubscription.class));
    }

    // ── activateSubscription() ───────────────────────────────────

    @Test
    void shouldActivateNewSubscriptionForNewUser() {
        SubscriptionPlan plan = buildPlan(2L, "lite", 1);
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        subscriptionService.activateSubscription(1001L, 2L, "MONTHLY");

        verify(subscriptionMapper).insert(any(UserSubscription.class));
    }

    @Test
    void shouldUpgradeExistingSubscription() {
        SubscriptionPlan plan = buildPlan(2L, "lite", 1);
        UserSubscription existing = buildSubscription(10L, 1001L, 1L, "ACTIVE", 0);
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(subscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        subscriptionService.activateSubscription(1001L, 2L, "YEARLY");

        assertThat(existing.getPlanId()).isEqualTo(2L);
        verify(subscriptionMapper).updateById(existing);
    }

    @Test
    void shouldThrowPlanNotExistWhenActivatingNonExistentPlan() {
        when(planMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> subscriptionService.activateSubscription(1001L, 9999L, "MONTHLY"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());
    }
}
