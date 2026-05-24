package com.zhiyu.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.entity.SubscriptionPlan;
import com.zhiyu.subscription.mapper.SubscriptionPlanMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private SubscriptionPlanMapper planMapper;

    @InjectMocks
    private PlanService planService;

    private static SubscriptionPlan buildPlan(Long id, String planKey, String name,
                                               int priceMonthly, int priceYearly,
                                               int isActive, int sortOrder) {
        return SubscriptionPlan.builder()
                .id(id)
                .planKey(planKey)
                .name(name)
                .description("Test plan")
                .priceMonthly(priceMonthly)
                .priceYearly(priceYearly)
                .trialDays(0)
                .featuresJson("[]")
                .quotasJson("{}")
                .isActive(isActive)
                .sortOrder(sortOrder)
                .build();
    }

    // ── listActivePlans() ──────────────────────────────────────────

    @Test
    void shouldReturnActivePlansOrderedBySortOrder() {
        SubscriptionPlan free = buildPlan(1L, "free", "Free", 0, 0, 1, 0);
        SubscriptionPlan lite = buildPlan(2L, "lite", "Lite", 2900, 29000, 1, 1);
        SubscriptionPlan pro = buildPlan(3L, "pro", "Pro", 9900, 99000, 1, 2);
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(free, lite, pro));

        List<PlanDto> result = planService.listActivePlans();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPlanKey()).isEqualTo("free");
        assertThat(result.get(1).getPlanKey()).isEqualTo("lite");
        assertThat(result.get(2).getPlanKey()).isEqualTo("pro");
        verify(planMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnEmptyListWhenNoActivePlans() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        List<PlanDto> result = planService.listActivePlans();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotIncludeInactivePlans() {
        SubscriptionPlan active = buildPlan(1L, "free", "Free", 0, 0, 1, 0);
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(active));

        List<PlanDto> result = planService.listActivePlans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlanKey()).isEqualTo("free");
    }

    // ── getPlan() ─────────────────────────────────────────────────

    @Test
    void shouldReturnPlanWhenPlanExists() {
        SubscriptionPlan plan = buildPlan(2L, "lite", "Lite", 2900, 29000, 1, 1);
        when(planMapper.selectById(2L)).thenReturn(plan);

        PlanDto result = planService.getPlan(2L);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getPlanKey()).isEqualTo("lite");
        assertThat(result.getName()).isEqualTo("Lite");
        assertThat(result.getPriceMonthly()).isEqualTo(2900);
        assertThat(result.getPriceYearly()).isEqualTo(29000);
        verify(planMapper).selectById(2L);
    }

    @Test
    void shouldThrowPlanNotExistWhenPlanNotFound() {
        when(planMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> planService.getPlan(9999L))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());

        verify(planMapper).selectById(9999L);
    }

    @Test
    void shouldReturnCorrectConversionForPlanWithAllFields() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(3L)
                .planKey("pro")
                .name("Pro")
                .description("Professional plan")
                .priceMonthly(9900)
                .priceYearly(99000)
                .trialDays(7)
                .featuresJson("[\"feature1\"]")
                .quotasJson("{\"daily_chat\":-1}")
                .isActive(1)
                .sortOrder(2)
                .build();
        when(planMapper.selectById(3L)).thenReturn(plan);

        PlanDto result = planService.getPlan(3L);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getPriceMonthly()).isEqualTo(9900);
        assertThat(result.getPriceYearly()).isEqualTo(99000);
        assertThat(result.getTrialDays()).isEqualTo(7);
        assertThat(result.getFeaturesJson()).isEqualTo("[\"feature1\"]");
        assertThat(result.getQuotasJson()).isEqualTo("{\"daily_chat\":-1}");
    }
}
