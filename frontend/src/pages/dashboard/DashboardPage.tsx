import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Spin, Alert, Button } from 'antd';
import {
  UserAddOutlined, LoginOutlined, TeamOutlined, CheckCircleOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart, PieChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, TitleComponent, LegendComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import apiClient from '../../api/client';

echarts.use([BarChart, PieChart, GridComponent, TooltipComponent,
  TitleComponent, LegendComponent, CanvasRenderer]);

interface StatsOverview {
  todayRegistrations: number;
  todayLogins: number;
  dau: number;
  loginSuccessRate: number;
  registrationChange: number;
  loginChange: number;
}

interface TrendPoint {
  date: string;
  count: number;
}

interface DistributionItem {
  method: string;
  count: number;
  percentage: number;
}

function DashboardPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [regTrend, setRegTrend] = useState<TrendPoint[]>([]);
  const [dauTrend, setDauTrend] = useState<TrendPoint[]>([]);
  const [dist, setDist] = useState<DistributionItem[]>([]);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, rt, dt, di] = await Promise.all([
        apiClient.get('/admin/stats/overview'),
        apiClient.get('/admin/stats/register-trend'),
        apiClient.get('/admin/stats/dau-trend'),
        apiClient.get('/admin/stats/login-method-dist'),
      ]);
      setOverview(ov.data?.data);
      setRegTrend(rt.data?.data || []);
      setDauTrend(dt.data?.data || []);
      setDist(di.data?.data || []);
    } catch (e) {
      setError('加载仪表盘数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  if (loading) {
    return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  }

  if (error) {
    return (
      <Alert type="error" message={error}
        action={<Button onClick={fetchData}>重试</Button>} />
    );
  }

  const barOption = (data: TrendPoint[], title: string) => ({
    tooltip: { trigger: 'axis' },
    title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
    grid: { top: 40, left: 40, right: 20, bottom: 30 },
    xAxis: { type: 'category', data: data.map((d) => d.date), axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: data.map((d) => d.count), itemStyle: { color: '#1677ff' } }],
  });

  const pieOption = {
    tooltip: { trigger: 'item' },
    title: { text: '登录方式分布', left: 'center', textStyle: { fontSize: 14 } },
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
          <Card><Statistic title="今日注册" value={overview?.todayRegistrations || 0}
            prefix={<UserAddOutlined />}
            suffix={overview ? `${overview.registrationChange >= 0 ? '+' : ''}${overview.registrationChange}%` : ''} />
          </Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="今日登录" value={overview?.todayLogins || 0}
            prefix={<LoginOutlined />}
            suffix={overview ? `${overview.loginChange >= 0 ? '+' : ''}${overview.loginChange}%` : ''} />
          </Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="日活用户 (DAU)" value={overview?.dau || 0}
            prefix={<TeamOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="登录成功率" value={overview?.loginSuccessRate || 0}
            prefix={<CheckCircleOutlined />} suffix="%" precision={1} />
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={12}>
          <Card><ReactEChartsCore option={barOption(regTrend, '近30天注册趋势')}
            style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card><ReactEChartsCore option={barOption(dauTrend, '近30天 DAU 趋势')}
            style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>
      <Row>
        <Col span={8}>
          <Card><ReactEChartsCore option={pieOption}
            style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default DashboardPage;
