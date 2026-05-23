package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public StatsOverviewResponse getOverview() {
        long todayRegs = countToday("auth_user", "created_time");
        long yesterdayRegs = countYesterday("auth_user", "created_time");
        long todayLogins = countToday("auth_user_log", "created_time");
        long yesterdayLogins = countYesterday("auth_user_log", "created_time");

        String dauSql = "SELECT COUNT(DISTINCT auth_user_log_user_id) FROM auth_user_log WHERE DATE(created_time) = CURDATE()";
        Long dau = jdbcTemplate.queryForObject(dauSql, Long.class);

        String rateSql = """
            SELECT
                ROUND(SUM(CASE WHEN auth_user_log_result='SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1)
            FROM auth_user_log
            WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND auth_user_log_action='LOGIN'
            """;
        Double rate = jdbcTemplate.queryForObject(rateSql, Double.class);

        double regChange = yesterdayRegs > 0
                ? ((double)(todayRegs - yesterdayRegs) / yesterdayRegs) * 100 : 100;
        double loginChange = yesterdayLogins > 0
                ? ((double)(todayLogins - yesterdayLogins) / yesterdayLogins) * 100 : 100;

        return StatsOverviewResponse.builder()
                .todayRegistrations(todayRegs)
                .todayLogins(todayLogins)
                .dau(dau != null ? dau : 0)
                .loginSuccessRate(rate != null ? rate : 0)
                .registrationChange(Math.round(regChange * 10.0) / 10.0)
                .loginChange(Math.round(loginChange * 10.0) / 10.0)
                .build();
    }

    public List<TrendPoint> getRegisterTrend(int days) {
        String sql = """
            SELECT DATE(created_time) as dt, COUNT(*) as cnt
            FROM auth_user WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            GROUP BY DATE(created_time) ORDER BY dt
            """;
        return jdbcTemplate.query(sql, (rs, i) -> TrendPoint.builder()
                .date(rs.getDate("dt").toString())
                .count(rs.getLong("cnt")).build(), days);
    }

    public List<TrendPoint> getDauTrend(int days) {
        String sql = """
            SELECT DATE(created_time) as dt, COUNT(DISTINCT auth_user_log_user_id) as cnt
            FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            AND auth_user_log_action='LOGIN' AND auth_user_log_result='SUCCESS'
            GROUP BY DATE(created_time) ORDER BY dt
            """;
        return jdbcTemplate.query(sql, (rs, i) -> TrendPoint.builder()
                .date(rs.getDate("dt").toString())
                .count(rs.getLong("cnt")).build(), days);
    }

    public List<DistributionItem> getLoginMethodDist(int days) {
        String sql = """
            SELECT auth_user_log_type as method, COUNT(*) as cnt
            FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            AND auth_user_log_action='LOGIN' AND auth_user_log_result='SUCCESS'
            GROUP BY auth_user_log_type
            """;
        var items = jdbcTemplate.query(sql, (rs, i) -> {
            long cnt = rs.getLong("cnt");
            return DistributionItem.builder()
                    .method(rs.getString("method") != null ? rs.getString("method") : "PASSWORD")
                    .count(cnt).percentage(0).build();
        }, days);
        long total = items.stream().mapToLong(DistributionItem::getCount).sum();
        items.forEach(item -> item.setPercentage(
                total > 0 ? Math.round(item.getCount() * 1000.0 / total) / 10.0 : 0));
        return items;
    }

    private long countToday(String table, String col) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE DATE(" + col + ") = CURDATE()";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }

    private long countYesterday(String table, String col) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE DATE(" + col + ") = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }
}
