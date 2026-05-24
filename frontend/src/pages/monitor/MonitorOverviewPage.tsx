import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Row, Col, Card, Statistic, Badge, Spin, Alert, Button } from 'antd';
import { ApiOutlined, BugOutlined, TeamOutlined } from '@ant-design/icons';
import monitorApi from '../../api/monitorApi';
import statsApi from '../../api/statsApi';
import type { HealthDto, StatsOverview } from '../../api/types';

const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  UP: 'success', DOWN: 'error', DEGRADED: 'warning',
};

function MonitorOverviewPage() {
  const { t } = useTranslation();
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
      setError(t('overview.loadFailed'));
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
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} />;

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>{t('overview.serviceHealth')}</h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        {health.map((h) => (
          <Col span={6} key={h.component}>
            <Card>
              <Statistic title={h.component}
                valueRender={() => <Badge status={STATUS_COLOR[h.status] || 'default'} text={h.status} />} />
              {h.responseTimeMs !== undefined && (
                <div style={{ fontSize: 12, color: '#999' }}>{t('overview.responseTime')}: {h.responseTimeMs}ms</div>
              )}
              <div style={{ fontSize: 11, color: '#999' }}>{t('overview.instanceCount')}: {h.instanceCount}</div>
            </Card>
          </Col>
        ))}
      </Row>
      <h3 style={{ marginBottom: 16 }}>{t('overview.todayOverview')}</h3>
      <Row gutter={16}>
        <Col span={8}>
          <Card><Statistic title={t('overview.todayApiCalls')} value={overview?.todayLogins || 0} prefix={<ApiOutlined />} /></Card>
        </Col>
        <Col span={8}>
          <Card><Statistic title={t('overview.todayErrors')} value={0} prefix={<BugOutlined />} /></Card>
        </Col>
        <Col span={8}>
          <Card><Statistic title={t('overview.onlineUsers')} value={overview?.onlineUsers || 0} prefix={<TeamOutlined />} /></Card>
        </Col>
      </Row>
    </div>
  );
}

export default MonitorOverviewPage;
