package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuthUserMapper authUserMapper;
    @Mock private AuthUserLogMapper authUserLogMapper;
    @InjectMocks private AdminStatsService adminStatsService;

    @Test
    void shouldGetOverview() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 3L, 2L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(95.5);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getTodayRegistrations()).isEqualTo(1L);
        assertThat(overview.getTodayLogins()).isEqualTo(3L);
        assertThat(overview.getDau()).isEqualTo(2L);
        assertThat(overview.getLoginSuccessRate()).isEqualTo(95.5);
    }

    @Test
    void shouldGetOverviewWithNullSafeDefaults() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(0L, 0L, null, 0L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(null);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getDau()).isEqualTo(0);
        assertThat(overview.getLoginSuccessRate()).isEqualTo(0);
    }

    @Test
    void shouldGetRegisterTrend() {
        TrendPoint point = TrendPoint.builder()
                .date("2026-05-23").count(5L).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(14)))
                .thenReturn(List.of(point));

        List<TrendPoint> trend = adminStatsService.getRegisterTrend(14);

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getDate()).isEqualTo("2026-05-23");
        assertThat(trend.get(0).getCount()).isEqualTo(5L);
    }

    @Test
    void shouldGetDauTrend() {
        TrendPoint p1 = TrendPoint.builder().date("2026-05-22").count(10L).build();
        TrendPoint p2 = TrendPoint.builder().date("2026-05-23").count(15L).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenReturn(List.of(p1, p2));

        List<TrendPoint> trend = adminStatsService.getDauTrend(7);

        assertThat(trend).hasSize(2);
        assertThat(trend.get(1).getCount()).isEqualTo(15L);
    }

    @Test
    void shouldGetLoginMethodDist() {
        DistributionItem password = DistributionItem.builder()
                .method("PASSWORD").count(80L).percentage(0).build();
        DistributionItem webauthn = DistributionItem.builder()
                .method("WEBAUTHN").count(20L).percentage(0).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(30)))
                .thenReturn(List.of(password, webauthn));

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(30);

        assertThat(dist).hasSize(2);
        assertThat(dist.get(0).getPercentage()).isGreaterThan(0);
        assertThat(dist.get(1).getPercentage()).isGreaterThan(0);
    }

    @Test
    void shouldHandleEmptyTrend() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(30)))
                .thenReturn(List.of());

        List<TrendPoint> trend = adminStatsService.getRegisterTrend(30);

        assertThat(trend).isEmpty();
    }

    @Test
    void shouldHandleEmptyDistribution() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenReturn(List.of());

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(7);

        assertThat(dist).isEmpty();
    }

    @Test
    void shouldGetLoginMethodDistWithNullMethod() {
        DistributionItem item = DistributionItem.builder()
                .method("PASSWORD").count(10L).percentage(0).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenReturn(List.of(item));

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(7);

        assertThat(dist).hasSize(1);
        assertThat(dist.get(0).getPercentage()).isEqualTo(100.0);
    }

    @Test
    void shouldGetLoginMethodDistWithSingleItem() {
        DistributionItem item = DistributionItem.builder()
                .method("TOTP").count(0L).percentage(0).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(30)))
                .thenReturn(List.of(item));

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(30);

        assertThat(dist).hasSize(1);
        assertThat(dist.get(0).getPercentage()).isEqualTo(0.0);
    }

    // ── getTrend ────────────────────────────────────────────────

    @Test
    void shouldGetTrendWithDefaultDays() {
        List<Map<String, Object>> trendData = List.of(
                Map.<String, Object>of("date", "2026-05-21",
                        "newUsers", 25L, "activeUsers", 120L),
                Map.<String, Object>of("date", "2026-05-22",
                        "newUsers", 30L, "activeUsers", 135L));

        when(jdbcTemplate.queryForList(anyString(), eq(7))).thenReturn(trendData);

        List<Map<String, Object>> result = adminStatsService.getTrend(7);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("date", "2026-05-21");
        assertThat(result.get(0)).containsEntry("newUsers", 25L);
        assertThat(result.get(1)).containsEntry("activeUsers", 135L);
    }

    @Test
    void shouldGetTrendWithCustomDays() {
        when(jdbcTemplate.queryForList(anyString(), eq(14))).thenReturn(List.of());

        List<Map<String, Object>> result = adminStatsService.getTrend(14);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGetTrendWith90Days() {
        List<Map<String, Object>> trendData = List.of(
                Map.<String, Object>of("date", "2026-02-23",
                        "newUsers", 10L, "activeUsers", 50L));

        when(jdbcTemplate.queryForList(anyString(), eq(90))).thenReturn(trendData);

        List<Map<String, Object>> result = adminStatsService.getTrend(90);

        assertThat(result).hasSize(1);
    }

    // ── getOverview additional edge cases ───────────────────────

    @Test
    void shouldGetOverviewWithYesterdayZero() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(5L, 0L, 3L, 0L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(100.0);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getTodayRegistrations()).isEqualTo(5L);
        assertThat(overview.getTodayLogins()).isEqualTo(3L);
    }

    @Test
    void shouldGetOverviewWhenAllZeros() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(0L, 0L, 0L, 0L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(0.0);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getTodayRegistrations()).isEqualTo(0L);
        assertThat(overview.getTodayLogins()).isEqualTo(0L);
        assertThat(overview.getDau()).isEqualTo(0L);
        assertThat(overview.getLoginSuccessRate()).isEqualTo(0.0);
        assertThat(overview.getNewUsers()).isEqualTo(0L);
    }

    @Test
    void shouldGetOverviewWithNegativeRegistrationChange() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(3L, 10L, 5L, 0L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(85.0);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getRegistrationChange()).isLessThan(0);
        assertThat(overview.getNewUsers()).isEqualTo(3L);
    }

    @Test
    void shouldGetOverviewWhenSubscriptionQueryThrows() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 3L, 2L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(95.5);
        // Make subscription, revenue, and online queries throw
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM zhiyu_subscription WHERE status = 'ACTIVE'"),
                eq(Long.class)))
                .thenThrow(new RuntimeException("table not found"));

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getActiveSubs()).isEqualTo(0);
    }

    @Test
    void shouldGetOverviewWhenRevenueQueryThrows() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 3L, 2L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(95.5);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COALESCE(SUM(amount), 0) FROM zhiyu_payment"
                        + " WHERE DATE(created_time) = CURDATE() AND status = 'SUCCESS'"),
                eq(Long.class)))
                .thenThrow(new RuntimeException("table not found"));

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getRevenue()).isEqualTo(0);
    }

    @Test
    void shouldGetOverviewWhenOnlineUsersQueryThrows() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(1L, 0L, 3L, 2L);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(95.5);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(DISTINCT auth_user_log_user_id)"
                        + " FROM auth_user_log WHERE created_time"
                        + " >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)"),
                eq(Long.class)))
                .thenThrow(new RuntimeException("query failed"));

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getOnlineUsers()).isEqualTo(0);
    }

    @Test
    void shouldGetRegisterTrendWithDifferentDays() {
        TrendPoint point = TrendPoint.builder()
                .date("2026-05-22").count(8L).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(90)))
                .thenReturn(List.of(point));

        List<TrendPoint> trend = adminStatsService.getRegisterTrend(90);

        assertThat(trend).hasSize(1);
    }

    @Test
    void shouldGetDauTrendWith90Days() {
        TrendPoint point = TrendPoint.builder()
                .date("2026-02-23").count(50L).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(90)))
                .thenReturn(List.of(point));

        List<TrendPoint> trend = adminStatsService.getDauTrend(90);

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getCount()).isEqualTo(50L);
    }

    @Test
    void shouldGetLoginMethodDistWithAllSameMethod() {
        DistributionItem item = DistributionItem.builder()
                .method("PASSWORD").count(100L).percentage(0).build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenReturn(List.of(item));

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(7);

        assertThat(dist).hasSize(1);
        assertThat(dist.get(0).getPercentage()).isEqualTo(100.0);
    }

    @Test
    void shouldGetOverviewWhenAllLongQueriesReturnNull() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class)))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Double.class)))
                .thenReturn(null);

        StatsOverviewResponse overview = adminStatsService.getOverview();

        assertThat(overview.getTodayRegistrations()).isEqualTo(0L);
        assertThat(overview.getTodayLogins()).isEqualTo(0L);
        assertThat(overview.getDau()).isEqualTo(0L);
        assertThat(overview.getLoginSuccessRate()).isEqualTo(0.0);
        assertThat(overview.getActiveSubs()).isEqualTo(0L);
        assertThat(overview.getRevenue()).isEqualTo(0L);
        assertThat(overview.getOnlineUsers()).isEqualTo(0L);
        assertThat(overview.getNewUsers()).isEqualTo(0L);
    }

    // ── RowMapper coverage via Answer ──────────────────────────

    @Test
    void shouldCoverRegisterTrendRowMapper() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenAnswer((Answer<List<TrendPoint>>) invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<TrendPoint> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getDate("dt")).thenReturn(Date.valueOf("2026-05-23"));
                    when(rs.getLong("cnt")).thenReturn(5L);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<TrendPoint> trend = adminStatsService.getRegisterTrend(7);

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getDate()).isEqualTo("2026-05-23");
        assertThat(trend.get(0).getCount()).isEqualTo(5L);
    }

    @Test
    void shouldCoverDauTrendRowMapper() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenAnswer((Answer<List<TrendPoint>>) invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<TrendPoint> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getDate("dt")).thenReturn(Date.valueOf("2026-05-22"));
                    when(rs.getLong("cnt")).thenReturn(10L);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<TrendPoint> trend = adminStatsService.getDauTrend(7);

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getDate()).isEqualTo("2026-05-22");
        assertThat(trend.get(0).getCount()).isEqualTo(10L);
    }

    @Test
    void shouldCoverLoginMethodDistRowMapperWithNonNullMethod() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenAnswer((Answer<List<DistributionItem>>) invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DistributionItem> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("method")).thenReturn("WEBAUTHN");
                    when(rs.getLong("cnt")).thenReturn(50L);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(7);

        assertThat(dist).hasSize(1);
        assertThat(dist.get(0).getMethod()).isEqualTo("WEBAUTHN");
        assertThat(dist.get(0).getCount()).isEqualTo(50L);
    }

    @Test
    void shouldCoverLoginMethodDistRowMapperWithNullMethod() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
                .thenAnswer((Answer<List<DistributionItem>>) invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DistributionItem> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("method")).thenReturn(null);
                    when(rs.getLong("cnt")).thenReturn(30L);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<DistributionItem> dist = adminStatsService.getLoginMethodDist(7);

        assertThat(dist).hasSize(1);
        assertThat(dist.get(0).getMethod()).isEqualTo("PASSWORD");
        assertThat(dist.get(0).getCount()).isEqualTo(30L);
    }
}
