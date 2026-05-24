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

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final SubscriptionPlanMapper planMapper;

    public List<PlanDto> listActivePlans() {
        List<SubscriptionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .eq(SubscriptionPlan::getIsActive, 1)
                        .orderByAsc(SubscriptionPlan::getSortOrder));
        return plans.stream()
                .map(SubscriptionConverter.INSTANCE::toPlanDto)
                .collect(Collectors.toList());
    }

    public PlanDto getPlan(final Long planId) {
        SubscriptionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(BizErrorCode.PLAN_NOT_EXIST);
        }
        return SubscriptionConverter.INSTANCE.toPlanDto(plan);
    }
}
