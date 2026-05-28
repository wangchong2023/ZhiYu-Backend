/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.converter.SubscriptionConverter;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智宇平台订阅套餐核心服务类。
 *
 * <p>主要处理套餐生命周期内的配置管理及状态检索，包括前台可用套餐的聚合转换、
 * 以及指定套餐记录的安全主键读取与防空拦截。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final SubscriptionPlanMapper planMapper;

    /**
     * 获取所有已启用并激活的套餐列表。
     *
     * <p>调用底层 Mapper 执行条件查询，结果将根据 {@link SubscriptionPlan#getSortOrder()} 升序进行权重排序，
     * 并通过 MapStruct 优雅地转化为展现层 DTO 列表返回。</p>
     *
     * @return 状态为已上架（isActive=1）的套餐 DTO 列表
     */
    public List<PlanDto> listActivePlans() {
        List<SubscriptionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .eq(SubscriptionPlan::getIsActive, 1)
                        .orderByAsc(SubscriptionPlan::getSortOrder));
        return plans.stream()
                .map(SubscriptionConverter.INSTANCE::toPlanDto)
                .collect(Collectors.toList());
    }

    /**
     * 根据主键 ID 安全读取指定套餐的配置信息。
     *
     * @param planId 目标套餐的主键 ID
     * @return 对应的套餐配置详情 DTO
     * @throws BizException 若查询的套餐 ID 在数据库中不存在，抛出 PLAN_NOT_EXIST 业务异常以防空指针漏洞
     */
    public PlanDto getPlan(final Long planId) {
        SubscriptionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }
        return SubscriptionConverter.INSTANCE.toPlanDto(plan);
    }
}
