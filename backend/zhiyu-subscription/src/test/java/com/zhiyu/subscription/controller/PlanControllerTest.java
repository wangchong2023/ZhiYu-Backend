package com.zhiyu.subscription.controller;

import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.subscription.dto.PlanDto;
import com.zhiyu.subscription.service.PlanService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;

    @InjectMocks
    private PlanController planController;

    @SuppressWarnings("checkstyle:MagicNumber")
    private static PlanDto buildPlanDto(Long id, String planKey, String name,
                                         int priceMonthly, int priceYearly) {
        return PlanDto.builder()
                .id(id).planKey(planKey).name(name)
                .description("Test plan")
                .priceMonthly(priceMonthly).priceYearly(priceYearly)
                .trialDays(0).featuresJson("[]").quotasJson("{}")
                .build();
    }

    // ── listActivePlans() ─────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldReturnActivePlansList() {
        List<PlanDto> expected = List.of(
                buildPlanDto(1L, "free", "Free", 0, 0),
                buildPlanDto(2L, "lite", "Lite", 2900, 29000));
        when(planService.listActivePlans()).thenReturn(expected);

        ApiResponse<List<PlanDto>> response = planController.listActivePlans();

        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getPlanKey()).isEqualTo("free");
        assertThat(response.getData().get(1).getPlanKey()).isEqualTo("lite");
        verify(planService).listActivePlans();
    }

    @Test
    void shouldReturnEmptyListWhenNoActivePlans() {
        when(planService.listActivePlans()).thenReturn(Collections.emptyList());

        ApiResponse<List<PlanDto>> response = planController.listActivePlans();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEmpty();
    }

    // ── getPlan() ─────────────────────────────────────────────────

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void shouldReturnPlanById() {
        PlanDto expected = buildPlanDto(2L, "lite", "Lite", 2900, 29000);
        when(planService.getPlan(2L)).thenReturn(expected);

        ApiResponse<PlanDto> response = planController.getPlan(2L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getId()).isEqualTo(2L);
        assertThat(response.getData().getPlanKey()).isEqualTo("lite");
        verify(planService).getPlan(2L);
    }

    @Test
    void shouldPropagateExceptionFromGetPlan() {
        when(planService.getPlan(9999L))
                .thenThrow(new BizException(BizErrorCode.PLAN_NOT_EXIST));

        assertThatThrownBy(() -> planController.getPlan(9999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.PLAN_NOT_EXIST.getCode());
    }
}
