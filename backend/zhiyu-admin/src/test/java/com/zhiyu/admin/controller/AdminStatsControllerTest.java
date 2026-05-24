package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.admin.service.AdminStatsService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    @Mock
    private AdminStatsService adminStatsService;

    @InjectMocks
    private AdminStatsController adminStatsController;

    // ─── overview ───

    @Test
    void shouldReturnOverview() {
        StatsOverviewResponse overview = StatsOverviewResponse.builder()
                .todayRegistrations(120L)
                .todayLogins(450L)
                .dau(320L)
                .loginSuccessRate(95.5)
                .registrationChange(12.5)
                .loginChange(-3.2)
                .newUsers(120L)
                .activeSubs(89L)
                .revenue(15200L)
                .onlineUsers(45L)
                .build();

        when(adminStatsService.getOverview()).thenReturn(overview);

        ApiResponse<StatsOverviewResponse> response = adminStatsController.overview();

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getTodayRegistrations()).isEqualTo(120L);
        assertThat(response.getData().getTodayLogins()).isEqualTo(450L);
        assertThat(response.getData().getDau()).isEqualTo(320L);
        assertThat(response.getData().getLoginSuccessRate()).isEqualTo(95.5);
        assertThat(response.getData().getRegistrationChange()).isEqualTo(12.5);
        assertThat(response.getData().getLoginChange()).isEqualTo(-3.2);
        assertThat(response.getData().getNewUsers()).isEqualTo(120L);
        assertThat(response.getData().getActiveSubs()).isEqualTo(89L);
        assertThat(response.getData().getRevenue()).isEqualTo(15200L);
        assertThat(response.getData().getOnlineUsers()).isEqualTo(45L);

        verify(adminStatsService).getOverview();
        verifyNoMoreInteractions(adminStatsService);
    }

    @Test
    void shouldReturnOverviewWithZeros() {
        StatsOverviewResponse overview = StatsOverviewResponse.builder()
                .todayRegistrations(0L).todayLogins(0L).dau(0L)
                .loginSuccessRate(0.0).registrationChange(0.0).loginChange(0.0)
                .newUsers(0L).activeSubs(0L).revenue(0L).onlineUsers(0L)
                .build();

        when(adminStatsService.getOverview()).thenReturn(overview);

        ApiResponse<StatsOverviewResponse> response = adminStatsController.overview();

        assertThat(response.getData().getTodayRegistrations()).isEqualTo(0L);
        assertThat(response.getData().getOnlineUsers()).isEqualTo(0L);

        verify(adminStatsService).getOverview();
    }

    // ─── registerTrend ───

    @Test
    void shouldReturnRegisterTrendWithDefaultDays() {
        List<TrendPoint> trend = List.of(
                TrendPoint.builder().date("2026-05-21").count(25L).build(),
                TrendPoint.builder().date("2026-05-22").count(30L).build(),
                TrendPoint.builder().date("2026-05-23").count(42L).build());

        when(adminStatsService.getRegisterTrend(30)).thenReturn(trend);

        ApiResponse<List<TrendPoint>> response = adminStatsController.registerTrend(30);

        assertThat(response.getData()).hasSize(3);
        assertThat(response.getData().get(0).getDate()).isEqualTo("2026-05-21");
        assertThat(response.getData().get(2).getCount()).isEqualTo(42L);

        verify(adminStatsService).getRegisterTrend(30);
        verifyNoMoreInteractions(adminStatsService);
    }

    @Test
    void shouldReturnRegisterTrendWithCustomDays() {
        List<TrendPoint> trend = List.of(
                TrendPoint.builder().date("2026-05-16").count(10L).build());

        when(adminStatsService.getRegisterTrend(7)).thenReturn(trend);

        ApiResponse<List<TrendPoint>> response = adminStatsController.registerTrend(7);

        assertThat(response.getData()).hasSize(1);
        verify(adminStatsService).getRegisterTrend(7);
    }

    @Test
    void shouldReturnEmptyRegisterTrend() {
        when(adminStatsService.getRegisterTrend(30)).thenReturn(List.of());

        ApiResponse<List<TrendPoint>> response = adminStatsController.registerTrend(30);

        assertThat(response.getData()).isEmpty();
        verify(adminStatsService).getRegisterTrend(30);
    }

    // ─── dauTrend ───

    @Test
    void shouldReturnDauTrendWithDefaultDays() {
        List<TrendPoint> trend = List.of(
                TrendPoint.builder().date("2026-05-21").count(120L).build(),
                TrendPoint.builder().date("2026-05-22").count(135L).build(),
                TrendPoint.builder().date("2026-05-23").count(150L).build());

        when(adminStatsService.getDauTrend(30)).thenReturn(trend);

        ApiResponse<List<TrendPoint>> response = adminStatsController.dauTrend(30);

        assertThat(response.getData()).hasSize(3);
        assertThat(response.getData().get(1).getCount()).isEqualTo(135L);

        verify(adminStatsService).getDauTrend(30);
    }

    @Test
    void shouldReturnDauTrendWithCustomDays() {
        when(adminStatsService.getDauTrend(7)).thenReturn(List.of());

        ApiResponse<List<TrendPoint>> response = adminStatsController.dauTrend(7);

        assertThat(response.getData()).isEmpty();
        verify(adminStatsService).getDauTrend(7);
    }

    @Test
    void shouldReturnDauTrendWith90Days() {
        List<TrendPoint> trend = List.of(
                TrendPoint.builder().date("2026-02-23").count(50L).build());

        when(adminStatsService.getDauTrend(90)).thenReturn(trend);

        ApiResponse<List<TrendPoint>> response = adminStatsController.dauTrend(90);

        assertThat(response.getData()).hasSize(1);
        verify(adminStatsService).getDauTrend(90);
    }

    // ─── loginMethodDist ───

    @Test
    void shouldReturnLoginMethodDistWithDefaultDays() {
        List<DistributionItem> dist = List.of(
                DistributionItem.builder().method("PASSWORD").count(200L).percentage(60.0).build(),
                DistributionItem.builder().method("SMS").count(100L).percentage(30.0).build(),
                DistributionItem.builder().method("GITHUB").count(33L).percentage(10.0).build());

        when(adminStatsService.getLoginMethodDist(30)).thenReturn(dist);

        ApiResponse<List<DistributionItem>> response = adminStatsController.loginMethodDist(30);

        assertThat(response.getData()).hasSize(3);
        assertThat(response.getData().get(0).getMethod()).isEqualTo("PASSWORD");
        assertThat(response.getData().get(0).getCount()).isEqualTo(200L);
        assertThat(response.getData().get(0).getPercentage()).isEqualTo(60.0);

        verify(adminStatsService).getLoginMethodDist(30);
    }

    @Test
    void shouldReturnLoginMethodDistWith7Days() {
        List<DistributionItem> dist = List.of(
                DistributionItem.builder().method("PASSWORD").count(50L).percentage(100.0).build());

        when(adminStatsService.getLoginMethodDist(7)).thenReturn(dist);

        ApiResponse<List<DistributionItem>> response = adminStatsController.loginMethodDist(7);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getPercentage()).isEqualTo(100.0);
        verify(adminStatsService).getLoginMethodDist(7);
    }

    // ─── trend ───

    @Test
    void shouldReturnTrendWithDefaultDays() {
        List<Map<String, Object>> trend = List.of(
                Map.<String, Object>of("date", "2026-05-21",
                        "newUsers", 25L, "activeUsers", 120L),
                Map.<String, Object>of("date", "2026-05-22",
                        "newUsers", 30L, "activeUsers", 135L));

        when(adminStatsService.getTrend(7)).thenReturn(trend);

        ApiResponse<List<Map<String, Object>>> response = adminStatsController.trend(7);

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0)).containsEntry("newUsers", 25L);

        verify(adminStatsService).getTrend(7);
        verifyNoMoreInteractions(adminStatsService);
    }

    @Test
    void shouldReturnTrendWithCustomDays() {
        when(adminStatsService.getTrend(14)).thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> response = adminStatsController.trend(14);

        assertThat(response.getData()).isEmpty();
        verify(adminStatsService).getTrend(14);
    }

    @Test
    void shouldReturnTrendWith1Day() {
        List<Map<String, Object>> trend = List.of(
                Map.<String, Object>of("date", "2026-05-23",
                        "newUsers", 42L, "activeUsers", 150L));

        when(adminStatsService.getTrend(1)).thenReturn(trend);

        ApiResponse<List<Map<String, Object>>> response = adminStatsController.trend(1);

        assertThat(response.getData()).hasSize(1);
        verify(adminStatsService).getTrend(1);
    }
}
