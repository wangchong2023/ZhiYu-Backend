package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private static final double HUNDRED = 100.0;
    private static final double TEN = 10.0;
    private static final double THOUSAND = 1000.0;

    private final JdbcTemplate jdbcTemplate;
    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public StatsOverviewResponse getOverview() {
        long todayRegs = countToday("auth_user", "created_time");
        long yesterdayRegs = countYesterday("auth_user", "created_time");
        long todayLogins = countToday("auth_user_log", "created_time");
        long yesterdayLogins = countYesterday("auth_user_log", "created_time");

        String dauSql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
                + " FROM auth_user_log WHERE DATE(created_time) = CURDATE()";
        Long dau = jdbcTemplate.queryForObject(dauSql, Long.class);

        String rateSql = """
            SELECT
                ROUND(SUM(CASE WHEN auth_user_log_result='SUCCESS' THEN 1 ELSE 0 END) \
            * 100.0 / COUNT(*), 1)
            FROM auth_user_log
            WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) \
            AND auth_user_log_action='LOGIN'
            """;
        Double rate = jdbcTemplate.queryForObject(rateSql, Double.class);

        double regChange = yesterdayRegs > 0
                ? ((double) (todayRegs - yesterdayRegs) / yesterdayRegs) * HUNDRED : HUNDRED;
        double loginChange = yesterdayLogins > 0
                ? ((double) (todayLogins - yesterdayLogins) / yesterdayLogins) * HUNDRED : HUNDRED;

        // New fields for expanded monitoring dashboard
        long newUsers = todayRegs;
        long activeSubs = countActiveSubscriptions();
        long revenue = countTodayRevenue();
        long onlineUsers = countOnlineUsers();

        return StatsOverviewResponse.builder()
                .todayRegistrations(todayRegs)
                .todayLogins(todayLogins)
                .dau(dau != null ? dau : 0)
                .loginSuccessRate(rate != null ? rate : 0)
                .registrationChange(Math.round(regChange * TEN) / TEN)
                .loginChange(Math.round(loginChange * TEN) / TEN)
                .newUsers(newUsers)
                .activeSubs(activeSubs)
                .revenue(revenue)
                .onlineUsers(onlineUsers)
                .build();
    }

    public List<TrendPoint> getRegisterTrend(final int days) {
        String sql = """
            SELECT DATE(created_time) as dt, COUNT(*) as cnt
            FROM auth_user WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            GROUP BY DATE(created_time) ORDER BY dt
            """;
        return jdbcTemplate.query(sql, (rs, i) -> TrendPoint.builder()
                .date(rs.getDate("dt").toString())
                .count(rs.getLong("cnt")).build(), days);
    }

    public List<TrendPoint> getDauTrend(final int days) {
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

    public List<DistributionItem> getLoginMethodDist(final int days) {
        String sql = """
            SELECT auth_user_log_type as method, COUNT(*) as cnt
            FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            AND auth_user_log_action='LOGIN' AND auth_user_log_result='SUCCESS'
            GROUP BY auth_user_log_type
            """;
        var items = jdbcTemplate.query(sql, (rs, i) -> {
            long cnt = rs.getLong("cnt");
            return DistributionItem.builder()
                    .method(rs.getString("method") != null
                            ? rs.getString("method") : "PASSWORD")
                    .count(cnt).percentage(0).build();
        }, days);
        long total = items.stream().mapToLong(DistributionItem::getCount).sum();
        items.forEach(item -> item.setPercentage(
                total > 0 ? Math.round(item.getCount() * THOUSAND / total) / TEN : 0));
        return items;
    }

    public List<Map<String, Object>> getTrend(final int days) {
        String sql = """
            SELECT
                DATE(a.created_time) as date,
                COUNT(DISTINCT a.auth_user_id) as newUsers,
                COUNT(DISTINCT l.auth_user_log_user_id) as activeUsers
            FROM auth_user a
            LEFT JOIN auth_user_log l ON DATE(l.created_time) = DATE(a.created_time)
                AND l.auth_user_log_action = 'LOGIN'
                AND l.auth_user_log_result = 'SUCCESS'
            WHERE a.created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
            GROUP BY DATE(a.created_time) ORDER BY date
            """;
        return jdbcTemplate.queryForList(sql, days);
    }

    private long countActiveSubscriptions() {
        try {
            String sql = "SELECT COUNT(*) FROM zhiyu_subscription WHERE status = 'ACTIVE'";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countTodayRevenue() {
        try {
            String sql = "SELECT COALESCE(SUM(amount), 0) FROM zhiyu_payment"
                    + " WHERE DATE(created_time) = CURDATE() AND status = 'SUCCESS'";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countOnlineUsers() {
        try {
            String sql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
                    + " FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countTodayApiCalls() {
        try {
            String sql = "SELECT COUNT(*) FROM auth_user_log"
                    + " WHERE DATE(created_time) = CURDATE()";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countToday(final String table, final String col) {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE DATE(" + col + ") = CURDATE()";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }

    private long countYesterday(final String table, final String col) {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE DATE(" + col + ") = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }
}
