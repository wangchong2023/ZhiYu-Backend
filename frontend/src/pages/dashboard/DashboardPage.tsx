import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Row, Col, Statistic, Button, List, Badge, Typography } from 'antd';
import {
  UserAddOutlined, DollarOutlined, TeamOutlined, WifiOutlined, WarningOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import statsApi from '../../api/statsApi';
import monitorApi from '../../api/monitorApi';
import { unwrap } from '../../utils/unwrap';
import { registerEcharts, CHART_COLORS, darkChartBase } from '../../utils/chartTheme';
import type { StatsOverview, TrendItem, DistributionItem, AlertDto } from '../../api/types';
import { PageLoader } from '../../components/PageLoader';

const { Title, Text } = Typography;

registerEcharts();

const severityColor = (s: string) => s === 'P0' ? 'red' : s === 'P1' ? 'orange' : 'gold';

function DashboardPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [trend, setTrend] = useState<TrendItem[]>([]);
  const [alerts, setAlerts] = useState<AlertDto[]>([]);
  const [dist, setDist] = useState<DistributionItem[]>([]);
  const [onlineUsers, setOnlineUsers] = useState(0);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, td, al, di] = await Promise.all([
        statsApi.overview(),
        statsApi.trend(7),
        monitorApi.recentAlerts(),
        statsApi.loginMethodDist(7),
      ]);
      const ovData = unwrap(ov);
      setOverview(ovData);
      setTrend(unwrap(td) || []);
      setAlerts(unwrap(al) || []);
      setDist(unwrap(di) || []);
      if (ovData) {
        setOnlineUsers(ovData.onlineUsers || 0);
      }
    } catch {
      setError(t('dashboard.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const res = await statsApi.overview();
        const data = unwrap(res);
        if (data) {
          setOnlineUsers(data.onlineUsers || 0);
        }
      } catch { /* ignore poll errors */ }
    }, 10000);
    return () => clearInterval(timer);
  }, []);

  const trendOption = trend.length > 0 ? {
    ...darkChartBase,
    tooltip: { trigger: 'axis' },
    title: { text: t('dashboard.trend7Days'), left: 'center', top: 4, textStyle: { fontSize: 13, color: CHART_COLORS.text } },
    legend: { data: [t('dashboard.dailyNewUsers'), t('dashboard.dailyActiveUsers')], bottom: 0, textStyle: { color: CHART_COLORS.text } },
    grid: { top: 40, left: 40, right: 20, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((d) => d.date), axisLabel: { rotate: 45, color: CHART_COLORS.text }, axisLine: { lineStyle: { color: CHART_COLORS.axis } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: CHART_COLORS.split } }, axisLabel: { color: CHART_COLORS.text } },
    series: [
      { name: t('dashboard.dailyNewUsers'), type: 'bar', data: trend.map((d) => d.newUsers), itemStyle: { color: CHART_COLORS.blue, borderRadius: [4, 4, 0, 0] } },
      { name: t('dashboard.dailyActiveUsers'), type: 'line', data: trend.map((d) => d.activeUsers), itemStyle: { color: CHART_COLORS.green }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 4 },
    ],
  } : null;

  const pieOption = dist.length > 0 ? {
    ...darkChartBase,
    tooltip: { trigger: 'item' },
    title: { text: t('dashboard.loginMethodDist'), left: 'center', top: 4, textStyle: { fontSize: 13, color: CHART_COLORS.text } },
    legend: { bottom: 0, textStyle: { color: CHART_COLORS.text } },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: dist.map((d) => ({ name: d.name, value: d.value })),
      label: { formatter: '{b}: {d}%', color: CHART_COLORS.text },
      itemStyle: { borderColor: 'var(--cosmic-deep)', borderWidth: 2 },
    }],
  } : null;

  const statStyle = { padding: '20px 24px' };
  const changeSuffix = (change: number) => (
    <span style={{ fontSize: 12, color: change >= 0 ? 'var(--cosmic-green)' : 'var(--cosmic-red)' }}>
      {`${change >= 0 ? '+' : ''}${change}%`}
    </span>
  );

  return (
    <PageLoader loading={loading} error={error} onRetry={fetchData}>
      {/* ── Stat Cards ── */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-1" style={statStyle}>
            <Statistic title={<span className="cosmic-label">{t('dashboard.newUsersToday')}</span>}
              value={overview?.newUsers || 0} valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700 }}
              prefix={<UserAddOutlined style={{ color: 'var(--cosmic-cyan-dim)' }} />}
              suffix={overview && changeSuffix(overview.registrationChange)} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-2" style={statStyle}>
            <Statistic title={<span className="cosmic-label">{t('dashboard.activeSubs')}</span>}
              value={overview?.activeSubs || 0} valueStyle={{ color: 'var(--cosmic-purple)', fontWeight: 700 }}
              prefix={<TeamOutlined style={{ color: 'rgba(129,140,248,0.3)' }} />} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-3" style={statStyle}>
            <Statistic title={<span className="cosmic-label">{t('dashboard.revenueToday')}</span>}
              value={overview?.revenue || 0} valueStyle={{ color: 'var(--cosmic-amber)', fontWeight: 700 }}
              prefix={<DollarOutlined style={{ color: 'var(--cosmic-amber-dim)' }} />}
              suffix={<span style={{ fontSize: 14, color: 'var(--cosmic-text-secondary)' }}>{t('dashboard.yuan')}</span>} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-4" style={statStyle}>
            <Statistic title={<span className="cosmic-label">{t('dashboard.onlineUsers')}</span>}
              value={onlineUsers} valueStyle={{ color: 'var(--cosmic-green)', fontWeight: 700 }}
              prefix={<WifiOutlined style={{ color: 'rgba(34,197,94,0.3)' }} />} />
          </div>
        </Col>
      </Row>

      {/* ── Trend + Alerts ── */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={16}>
          <div className="glass-panel cosmic-enter cosmic-stagger-5" style={{ padding: 20, minHeight: 300 }}>
            {trendOption ? (
              <ReactEChartsCore echarts={echarts} option={trendOption} style={{ height: 320 }} />
            ) : (
              <div style={{ height: 320, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--cosmic-text-muted)', fontSize: 13 }}>
                {t('dashboard.noTrendData')}
              </div>
            )}
          </div>
        </Col>
        <Col span={8}>
          <div className="glass-panel cosmic-enter cosmic-stagger-6" style={{ padding: '20px 20px 12px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span className="cosmic-heading" style={{ fontSize: 13 }}>{t('dashboard.recentAlerts')}</span>
              <Typography.Link onClick={() => { window.location.href = '/admin/monitor/alerts'; }}
                style={{ fontSize: 12, color: 'var(--cosmic-cyan)' }}>
                {t('dashboard.viewAll')}
              </Typography.Link>
            </div>
            {alerts.length === 0 ? (
              <div style={{ color: 'var(--cosmic-text-muted)', textAlign: 'center', padding: 24, fontSize: 13 }}>{t('dashboard.noAlerts')}</div>
            ) : (
              <List dataSource={alerts.slice(0, 5)} split={false}
                renderItem={(item) => (
                  <List.Item style={{ padding: '8px 0', borderBottom: '1px solid var(--cosmic-border)' }}>
                    <List.Item.Meta
                      avatar={<Badge color={severityColor(item.severity)} />}
                      title={<span style={{ fontSize: 13, color: 'var(--cosmic-text-primary)' }}>{item.alertName}</span>}
                      description={<span style={{ fontSize: 11, color: 'var(--cosmic-text-muted)' }}>{item.condition} — {item.firedAt}</span>}
                    />
                  </List.Item>
                )} />
            )}
          </div>
        </Col>
      </Row>

      {/* ── Pie Chart ── */}
      {pieOption && (
        <Row>
          <Col span={8}>
            <div className="glass-panel" style={{ padding: 20 }}>
              <ReactEChartsCore echarts={echarts} option={pieOption} style={{ height: 300 }} />
            </div>
          </Col>
        </Row>
      )}
    </PageLoader>
  );
}

export default DashboardPage;
