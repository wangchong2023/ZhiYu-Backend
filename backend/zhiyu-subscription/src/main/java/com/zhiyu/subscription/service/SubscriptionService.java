package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.converter.SubscriptionConverter;
import com.zhiyu.subscription.dto.SubscriptionDto;
import com.zhiyu.subscription.entity.QuotaUsage;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.entity.UserSubscription;
import com.zhiyu.subscription.mapper.QuotaUsageMapper;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.subscription.mapper.UserSubscriptionMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELING = "CANCELING";
    private static final String PLAN_KEY_FREE = "free";
    private static final long FAR_FUTURE_YEARS = 100;

    private final UserSubscriptionMapper subscriptionMapper;
    private final SubscriptionPlanMapper planMapper;
    private final QuotaUsageMapper quotaUsageMapper;

    @Transactional(rollbackFor = Exception.class)
    public void activateDefaultPlan(final Long userId) {
        SubscriptionPlan freePlan = planMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .eq(SubscriptionPlan::getPlanKey, PLAN_KEY_FREE)
                        .eq(SubscriptionPlan::getIsActive, 1));
        if (freePlan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        UserSubscription sub = UserSubscription.builder()
                .userId(userId)
                .planId(freePlan.getId())
                .status(STATUS_ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(FAR_FUTURE_YEARS))
                .autoRenew(0)
                .trialUsed(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        subscriptionMapper.insert(sub);
        log.info("Default plan activated for userId={}, planKey={}", userId, PLAN_KEY_FREE);
    }

    public SubscriptionDto getCurrentSubscription(final Long userId) {
        UserSubscription sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));
        if (sub == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        SubscriptionPlan plan = planMapper.selectById(sub.getPlanId());
        SubscriptionDto dto = SubscriptionConverter.INSTANCE.toSubscriptionDto(sub);
        if (plan != null) {
            dto.setPlanName(plan.getName());
        }
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void checkQuota(final Long userId, final String quotaKey, final int limit) {
        LocalDate today = LocalDate.now();
        // Current period: today to today
        LambdaQueryWrapper<QuotaUsage> wrapper = new LambdaQueryWrapper<QuotaUsage>()
                .eq(QuotaUsage::getUserId, userId)
                .eq(QuotaUsage::getQuotaKey, quotaKey)
                .eq(QuotaUsage::getPeriodStart, today);
        QuotaUsage quota = quotaUsageMapper.selectOne(wrapper);

        if (quota == null) {
            quota = QuotaUsage.builder()
                    .userId(userId)
                    .quotaKey(quotaKey)
                    .usedCount(1L)
                    .version(0)
                    .periodStart(today)
                    .periodEnd(today)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            quotaUsageMapper.insert(quota);
            return;
        }

        if (limit > 0 && quota.getUsedCount() >= limit) {
            throw new BizException(BizErrorCode.DAILY_QUOTA_EXHAUSTED);
        }

        // Optimistic lock: update by version
        Long oldUsedCount = quota.getUsedCount();
        quota.setUsedCount(oldUsedCount + 1);
        quota.setVersion(quota.getVersion() + 1);
        quota.setUpdatedAt(LocalDateTime.now());
        int updated = quotaUsageMapper.update(quota,
                new LambdaQueryWrapper<QuotaUsage>()
                        .eq(QuotaUsage::getId, quota.getId())
                        .eq(QuotaUsage::getVersion, quota.getVersion() - 1));
        if (updated == 0) {
            throw new BizException(BizErrorCode.DAILY_QUOTA_EXHAUSTED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelSubscription(final Long userId) {
        UserSubscription sub = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));
        if (sub == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        sub.setStatus(STATUS_CANCELING);
        sub.setCancelledAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionMapper.updateById(sub);
        log.info("Subscription cancelled for userId={}", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void activateSubscription(final Long userId, final Long planId,
                                      final String period) {
        SubscriptionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        LocalDate startDate = LocalDate.now();
        LocalDate endDate;
        if ("YEARLY".equals(period)) {
            endDate = startDate.plusYears(1);
        } else {
            endDate = startDate.plusMonths(1);
        }

        UserSubscription existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));
        if (existing != null) {
            existing.setPlanId(planId);
            existing.setStatus(STATUS_ACTIVE);
            existing.setStartDate(startDate);
            existing.setEndDate(endDate);
            existing.setUpdatedAt(LocalDateTime.now());
            subscriptionMapper.updateById(existing);
        } else {
            UserSubscription sub = UserSubscription.builder()
                    .userId(userId)
                    .planId(planId)
                    .status(STATUS_ACTIVE)
                    .startDate(startDate)
                    .endDate(endDate)
                    .autoRenew(0)
                    .trialUsed(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            subscriptionMapper.insert(sub);
        }
        log.info("Subscription activated for userId={}, planId={}, period={}", userId, planId, period);
    }
}
