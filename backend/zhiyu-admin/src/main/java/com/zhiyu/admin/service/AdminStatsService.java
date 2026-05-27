/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AdminStatsService.java
 * 创建时间: 2026-05-27
 * 描述: 后台管理统计分析业务服务。通过直接执行精细化 SQL 对系统数据（用户注册、活跃度、支付收入、用户分布等）进行图表统计与总览汇聚。
 */
package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.DistributionItem;
import com.zhiyu.admin.dto.StatsOverviewResponse;
import com.zhiyu.admin.dto.TrendPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 类名: AdminStatsService
 * 描述: 管理后台图表数据统计核心逻辑类。封装了多维度图表统计（包括趋势图、用户分布、DAU 和财务总计数据）。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    // 创建时间列名常量
    private static final String COLUMN_CREATED_TIME = "created_time";

    // 默认的图表统计时间跨度（天）
    private static final int DEFAULT_STATS_DAYS = 30;
    // 在线活跃用户的最大时间窗口判定（分钟）
    private static final int ONLINE_USER_THRESHOLD_MINUTES = 10;

    private static final double HUNDRED = 100.0;
    private static final double TEN = 10.0;
    private static final double THOUSAND = 1000.0;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 描述: 获取系统当前的各项关键指标数据总览，包括今日注册量、昨日注册量、DAU、用户留存/登录成功率及各项财务与在线状态。
     * @return 统计指标载体对象 StatsOverviewResponse
     */
    public StatsOverviewResponse getOverview() {
        long todayRegs = countToday("auth_user", COLUMN_CREATED_TIME);
        long yesterdayRegs = countYesterday("auth_user", COLUMN_CREATED_TIME);
        long todayLogins = countToday("auth_user_log", COLUMN_CREATED_TIME);
        long yesterdayLogins = countYesterday("auth_user_log", COLUMN_CREATED_TIME);

        String dauSql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
                + " FROM auth_user_log WHERE DATE(created_time) = CURDATE()";
        Long dau = jdbcTemplate.queryForObject(dauSql, Long.class);

        String rateSql = "SELECT"
                + " ROUND(SUM(CASE WHEN auth_user_log_result='SUCCESS' THEN 1 ELSE 0 END)"
                + " * 100.0 / COUNT(*), 1)"
                + " FROM auth_user_log"
                + " WHERE created_time >= DATE_SUB(NOW(), INTERVAL " + DEFAULT_STATS_DAYS + " DAY)"
                + " AND auth_user_log_action='LOGIN'";
        Double rate = jdbcTemplate.queryForObject(rateSql, Double.class);

        double regChange;
        if (yesterdayRegs > 0) {
            regChange = (double) (todayRegs - yesterdayRegs) / yesterdayRegs * HUNDRED;
        } else if (todayRegs > 0) {
            regChange = HUNDRED;
        } else {
            regChange = 0;
        }

        double loginChange;
        if (yesterdayLogins > 0) {
            loginChange = (double) (todayLogins - yesterdayLogins) / yesterdayLogins * HUNDRED;
        } else if (todayLogins > 0) {
            loginChange = HUNDRED;
        } else {
            loginChange = 0;
        }

        // 扩展监控面板新增字段
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

    /**
     * 描述: 获取过去指定天数内的每日新增用户注册数量的走势列表。
     * @param days 统计历史天数
     * @return 每日注册走势折线图点集
     */
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

    /**
     * 描述: 获取过去指定天数内日活跃用户数（DAU）的走势列表。
     * @param days 统计历史天数
     * @return DAU 每日统计折线图点集
     */
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

    /**
     * 描述: 获取登录方式占比数据（如密码登录、WebAuthn 登录等比例分布）。
     * @param days 统计历史天数
     * @return 登录渠道分类占比数据
     */
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

    /**
     * 描述: 获取过去指定天数内的整体用户新增与登录活跃交叉数据的趋势图。
     * @param days 统计历史天数
     * @return 新增与活跃数据的每日统计列表
     */
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

    /**
     * 描述: 私有辅助方法，统计当前处于 ACTIVE 有效状态的订阅会员总数。
     */
    private long countActiveSubscriptions() {
        try {
            String sql = "SELECT COUNT(*) FROM zhiyu_subscription WHERE status = 'ACTIVE'";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 描述: 私有辅助方法，统计今日成功完成的所有订单支付流水总收益。
     */
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

    /**
     * 描述: 私有辅助方法，以过去特定时长窗口内有请求访问行为的唯一活跃用户判定为在线人数。
     */
    private long countOnlineUsers() {
        try {
            String sql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
                    + " FROM auth_user_log"
                    + " WHERE created_time >= DATE_SUB(NOW(), INTERVAL " + ONLINE_USER_THRESHOLD_MINUTES + " MINUTE)";
            Long val = jdbcTemplate.queryForObject(sql, Long.class);
            return val != null ? val : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 描述: 通用辅助方法，根据给定的表名和时间日期列名，统计今日该表内的新建记录总数。
     */
    private long countToday(final String table, final String col) {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE DATE(" + col + ") = CURDATE()";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }

    /**
     * 描述: 通用辅助方法，根据给定的表名和时间日期列名，统计昨日该表内的新建记录总数。
     */
    private long countYesterday(final String table, final String col) {
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE DATE(" + col + ") = DATE_SUB(CURDATE(), INTERVAL 1 DAY)";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    }
}
