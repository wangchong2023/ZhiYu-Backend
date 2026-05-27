/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SubscriptionService.java
 * 创建时间: 2026-05-27
 * 描述: 订阅业务服务，提供订阅计划激活、配额检查、订阅取消等核心功能。
 */
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

/**
 * 类名: SubscriptionService
 * 描述: 用户订阅管理核心服务。
 *       负责新用户默认计划激活、当前订阅查询、使用配额管控（乐观锁）及订阅取消等全生命周期管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** 活跃订阅状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 取消中订阅状态（账期内仍可用） */
    private static final String STATUS_CANCELING = "CANCELING";
    /** 免费套餐的计划 Key */
    private static final String PLAN_KEY_FREE = "free";
    /** 免费计划的虚拟有效期年限（视为永久有效） */
    private static final long FAR_FUTURE_YEARS = 100;

    private final UserSubscriptionMapper subscriptionMapper;
    private final SubscriptionPlanMapper planMapper;
    private final QuotaUsageMapper quotaUsageMapper;

    /**
     * 描述: 为新注册用户激活默认的免费订阅计划。
     *      若免费计划不存在（配置缺失），则抛出业务异常中断注册流程。
     *
     * @param userId 用户ID
     * @throws BizException 免费计划不存在时抛出
     */
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
        log.info("用户默认计划已激活：userId={}, planKey={}", userId, PLAN_KEY_FREE);
    }

    /**
     * 描述: 查询用户当前有效的订阅信息。
     *
     * @param userId 用户ID
     * @return 包含计划名称的订阅 DTO
     * @throws BizException 用户无有效订阅时抛出
     */
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

    /**
     * 描述: 检查并扣减用户指定配额的当日用量。
     *      采用乐观锁（version 字段）防止并发重复扣减。
     *      首次使用时自动创建当日配额记录；超过限额时抛出业务异常。
     *
     * @param userId    用户ID
     * @param quotaKey  配额类型键（如 "ai_chat"、"sms_send"）
     * @param limit     每日上限（0 表示不限制）
     * @throws BizException 配额耗尽或乐观锁冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkQuota(final Long userId, final String quotaKey, final int limit) {
        LocalDate today = LocalDate.now();
        // 按用户、配额类型及当日起始日期精确查询配额记录
        LambdaQueryWrapper<QuotaUsage> wrapper = new LambdaQueryWrapper<QuotaUsage>()
                .eq(QuotaUsage::getUserId, userId)
                .eq(QuotaUsage::getQuotaKey, quotaKey)
                .eq(QuotaUsage::getPeriodStart, today);
        QuotaUsage quota = quotaUsageMapper.selectOne(wrapper);

        if (quota == null) {
            // 当日首次使用，初始化配额记录（used=1，起止日期均为今日）
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

        // 使用乐观锁更新用量：where id=? AND version=旧版本号，防止并发重复扣减
        Long oldUsedCount = quota.getUsedCount();
        quota.setUsedCount(oldUsedCount + 1);
        quota.setVersion(quota.getVersion() + 1);
        quota.setUpdatedAt(LocalDateTime.now());
        int updated = quotaUsageMapper.update(quota,
                new LambdaQueryWrapper<QuotaUsage>()
                        .eq(QuotaUsage::getId, quota.getId())
                        .eq(QuotaUsage::getVersion, quota.getVersion() - 1));
        if (updated == 0) {
            // 乐观锁冲突，说明有并发请求已更新，此次视为配额耗尽处理
            throw new BizException(BizErrorCode.DAILY_QUOTA_EXHAUSTED);
        }
    }

    /**
     * 描述: 用户取消当前订阅（进入取消中状态，账期内仍可正常使用）。
     *
     * @param userId 用户ID
     * @throws BizException 用户无有效订阅时抛出
     */
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
        log.info("订阅已取消（账期内仍可用）：userId={}", userId);
    }

    /**
     * 描述: 用户完成支付后激活指定计划的订阅。
     *      若用户已有订阅记录则更新，否则创建新记录。
     *      按计费周期（月/年）计算有效期结束日期。
     *
     * @param userId  用户ID
     * @param planId  目标订阅计划ID
     * @param period  计费周期（"MONTHLY" 月付，"YEARLY" 年付）
     * @throws BizException 订阅计划不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateSubscription(final Long userId, final Long planId,
                                      final String period) {
        SubscriptionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }

        LocalDate startDate = LocalDate.now();
        LocalDate endDate;
        // 根据计费周期计算订阅到期日
        if ("YEARLY".equals(period)) {
            endDate = startDate.plusYears(1);
        } else {
            endDate = startDate.plusMonths(1);
        }

        UserSubscription existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId));
        if (existing != null) {
            // 用户已有订阅记录：升级/变更计划
            existing.setPlanId(planId);
            existing.setStatus(STATUS_ACTIVE);
            existing.setStartDate(startDate);
            existing.setEndDate(endDate);
            existing.setUpdatedAt(LocalDateTime.now());
            subscriptionMapper.updateById(existing);
        } else {
            // 首次激活：创建新订阅记录
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
        log.info("订阅已激活：userId={}, planId={}, period={}", userId, planId, period);
    }
}
