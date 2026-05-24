import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Row, Col, Card, Statistic, Spin, Alert, Button, List, Badge, Typography } from 'antd';
import {
  UserAddOutlined, DollarOutlined, TeamOutlined, WifiOutlined, WarningOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, TitleComponent, LegendComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import apiClient from '../../api/client';

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent,
  TitleComponent, LegendComponent, CanvasRenderer]);

interface StatsOverview {
  newUsers: number; activeSubs: number; revenue: number; onlineUsers: number;
  todayRegistrations: number; todayLogins: number; dau: number;
  loginSuccessRate: number; registrationChange: number; loginChange: number;
}

interface TrendItem {
  date: string; newUsers: number; activeUsers: number;
}

interface AlertItem {
  alertName: string; severity: string; condition: string;
  currentValue: string; status: string; firedAt: string;
}

interface DistributionItem {
  method: string; count: number; percentage: number;
}

function DashboardPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [trend, setTrend] = useState<TrendItem[]>([]);
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [dist, setDist] = useState<DistributionItem[]>([]);
  const [onlineUsers, setOnlineUsers] = useState(0);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, td, al, di] = await Promise.all([
        apiClient.get('/admin/stats/overview'),
        apiClient.get('/admin/stats/trend', { params: { days: 7 } }),
        apiClient.get('/admin/monitor/alerts/recent'),
        apiClient.get('/admin/stats/login-method-dist'),
      ]);
      setOverview(ov.data?.data);
      setTrend(td.data?.data || []);
      setAlerts(al.data?.data || []);
      setDist(di.data?.data || []);
      setOnlineUsers(ov.data?.data?.onlineUsers || 0);
    } catch {
      setError(t('dashboard.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const ov = await apiClient.get('/admin/stats/overview');
        setOnlineUsers(ov.data?.data?.onlineUsers || 0);
      } catch { /* ignore poll errors */ }
    }, 10000);
    return () => clearInterval(timer);
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} />;

  const severityColor = (s: string) => s === 'P0' ? 'red' : s === 'P1' ? 'orange' : 'gold';

  const trendOption = trend.length > 0 ? {
    tooltip: { trigger: 'axis' },
    title: { text: t('dashboard.trend7Days'), left: 'center', textStyle: { fontSize: 14 } },
    legend: { data: [t('dashboard.dailyNewUsers'), t('dashboard.dailyActiveUsers')], bottom: 0 },
    grid: { top: 40, left: 40, right: 20, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((d) => d.date), axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    series: [
      { name: t('dashboard.dailyNewUsers'), type: 'bar', data: trend.map((d) => d.newUsers), itemStyle: { color: '#1677ff' } },
      { name: t('dashboard.dailyActiveUsers'), type: 'line', data: trend.map((d) => d.activeUsers), itemStyle: { color: '#52c41a' } },
    ],
  } : null;

  const pieOption = {
    tooltip: { trigger: 'item' },
    title: { text: t('dashboard.loginMethodDist'), left: 'center', textStyle: { fontSize: 14 } },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: dist.map((d) => ({ name: d.method, value: d.count })),
      label: { formatter: '{b}: {d}%' },
    }],
  };

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title={t('dashboard.newUsersToday')} value={overview?.newUsers || 0}
              prefix={<UserAddOutlined />}
              suffix={overview ? <span style={{ fontSize: 12, color: overview.registrationChange >= 0 ? '#52c41a' : '#ff4d4f' }}>{`${overview.registrationChange >= 0 ? '+' : ''}${overview.registrationChange}%`}</span> : undefined} />
          </Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('dashboard.activeSubs')} value={overview?.activeSubs || 0} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('dashboard.revenueToday')} value={overview?.revenue || 0} prefix={<DollarOutlined />} suffix={t('dashboard.yuan')} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('dashboard.onlineUsers')} value={onlineUsers} prefix={<WifiOutlined />} /></Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={16}>
          <Card>
            {trendOption ? (
              <ReactEChartsCore option={trendOption} style={{ height: 300 }} />
            ) : (
              <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>{t('dashboard.noTrendData')}</div>
            )}
          </Card>
        </Col>
        <Col span={8}>
          <Card title={t('dashboard.recentAlerts')} extra={<Typography.Link onClick={() => { window.location.href = '/admin/monitor/alerts'; }}>{t('dashboard.viewAll')}</Typography.Link>}>
            {alerts.length === 0 ? (
              <div style={{ color: '#999', textAlign: 'center', padding: 24 }}>{t('dashboard.noAlerts')}</div>
            ) : (
              <List dataSource={alerts.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      avatar={<Badge color={severityColor(item.severity)} />}
                      title={<Typography.Text style={{ fontSize: 13 }}>{item.alertName}</Typography.Text>}
                      description={<Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.condition} — {item.firedAt}</Typography.Text>}
                    />
                  </List.Item>
                )} />
            )}
          </Card>
        </Col>
      </Row>
      <Row>
        <Col span={8}>
          <Card><ReactEChartsCore option={pieOption} style={{ height: 300 }} /></Card>
        </Col>
      </Row>
    </div>
  );
}

export default DashboardPage;
