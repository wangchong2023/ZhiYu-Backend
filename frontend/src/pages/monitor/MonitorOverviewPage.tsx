import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Badge, Spin, Alert, Button } from 'antd';
import { ApiOutlined, BugOutlined, TeamOutlined } from '@ant-design/icons';
import monitorApi from '../../api/monitorApi';
import statsApi from '../../api/statsApi';
import type { HealthDto, StatsOverview } from '../../api/types';

const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  UP: 'success', DOWN: 'error', DEGRADED: 'warning',
};

function MonitorOverviewPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<HealthDto[]>([]);
  const [overview, setOverview] = useState<StatsOverview | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, ov] = await Promise.all([monitorApi.health(), statsApi.overview()]);
      setHealth(h.data?.data || []);
      setOverview(ov.data?.data || null);
    } catch {
      setError('加载监控数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  useEffect(() => {
    const timer = setInterval(fetchData, 30000);
    return () => clearInterval(timer);
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} />;

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>服务健康</h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        {health.map((h) => (
          <Col span={6} key={h.component}>
            <Card>
              <Statistic title={h.component}
                valueRender={() => <Badge status={STATUS_COLOR[h.status] || 'default'} text={h.status} />} />
              {h.responseTimeMs !== undefined && (
                <div style={{ fontSize: 12, color: '#999' }}>响应时间: {h.responseTimeMs}ms</div>
              )}
              <div style={{ fontSize: 11, color: '#999' }}>实例数: {h.instanceCount}</div>
            </Card>
          </Col>
        ))}
      </Row>
      <h3 style={{ marginBottom: 16 }}>今日概览</h3>
      <Row gutter={16}>
        <Col span={8}>
          <Card><Statistic title="今日 API 调用量" value={overview?.todayLogins || 0} prefix={<ApiOutlined />} /></Card>
        </Col>
        <Col span={8}>
          <Card><Statistic title="今日错误数" value={0} prefix={<BugOutlined />} /></Card>
        </Col>
        <Col span={8}>
          <Card><Statistic title="当前在线用户" value={overview?.onlineUsers || 0} prefix={<TeamOutlined />} /></Card>
        </Col>
      </Row>
    </div>
  );
}

export default MonitorOverviewPage;
