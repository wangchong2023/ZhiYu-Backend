import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Row, Col, Statistic, Badge, Tooltip, Table, Tag } from 'antd';
import { ApiOutlined, BugOutlined, TeamOutlined, InfoCircleOutlined } from '@ant-design/icons';
import monitorApi from '../../api/monitorApi';
import statsApi from '../../api/statsApi';
import type { HealthDto, StatsOverview, PodInfo } from '../../api/types';
import { unwrap } from '../../utils/unwrap';
import { PageLoader } from '../../components/PageLoader';

const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  UP: 'success', DOWN: 'error', DEGRADED: 'warning',
};

function useHealthLabel(t: (key: string) => string): Record<string, string> {
  return {
    app: t('monitor.health.app'),
    db: t('monitor.health.db'),
    redis: t('monitor.health.redis'),
    nacos: t('monitor.health.nacos'),
    diskSpace: t('monitor.health.diskSpace'),
  };
}

function MonitorOverviewPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<HealthDto[]>([]);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [pods, setPods] = useState<PodInfo[]>([]);
  const healthLabel = useHealthLabel(t);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, ov, pd] = await Promise.all([
        monitorApi.health(),
        statsApi.overview(),
        monitorApi.pods(),
      ]);
      setHealth(unwrap(h) || []);
      setOverview(unwrap(ov) || null);
      setPods(unwrap(pd) || []);
    } catch {
      setError(t('overview.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    const timer = setInterval(fetchData, 30000);
    return () => clearInterval(timer);
  }, [fetchData]);

  const dotCls = (status: string) => {
    if (status === 'UP') return 'cosmic-dot cosmic-dot-up';
    if (status === 'DOWN') return 'cosmic-dot cosmic-dot-down';
    if (status === 'DEGRADED') return 'cosmic-dot cosmic-dot-degraded';
    return 'cosmic-dot';
  };

  return (
    <PageLoader loading={loading} error={error} onRetry={fetchData}>
      {/* ── Service Health ── */}
      <h3 className="cosmic-heading" style={{ marginBottom: 16, fontSize: 15 }}>{t('overview.serviceHealth')}</h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        {health.map((h, i) => (
          <Col span={6} key={h.component}>
            <div className={`glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-${i + 1}`}
              style={{ padding: '20px 24px' }}>
              <Statistic title={
                <span className="cosmic-label">
                  {healthLabel[h.component] || h.component}
                  <Tooltip title={
                    <div style={{ fontSize: 12 }}>
                      <div><strong>Component:</strong> {h.component}</div>
                      {h.detail && <div style={{ marginTop: 4, maxWidth: 360, wordBreak: 'break-all' }}>{h.detail}</div>}
                    </div>
                  }>
                    <InfoCircleOutlined style={{ marginLeft: 6, fontSize: 12, color: 'var(--cosmic-text-muted)', cursor: 'help' }} />
                  </Tooltip>
                </span>
              }
                value=" "
                valueRender={() => (
                  <span style={{ fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <span className={dotCls(h.status)} />
                    <Badge status={STATUS_COLOR[h.status] || 'default'} text={h.status} />
                  </span>
                )} />
              {h.responseTimeMs !== undefined && (
                <div style={{ fontSize: 11, color: 'var(--cosmic-text-muted)', marginTop: 4 }}>
                  {t('overview.responseTime')}: {h.responseTimeMs}ms
                </div>
              )}
              <div style={{ fontSize: 10, color: 'var(--cosmic-text-muted)' }}>
                {t('overview.instanceCount')}: {h.instanceCount}
              </div>
            </div>
          </Col>
        ))}
      </Row>

      {/* ── Today Overview ── */}
      <h3 className="cosmic-heading" style={{ marginBottom: 16, fontSize: 15 }}>{t('overview.todayOverview')}</h3>
      <Row gutter={16}>
        <Col span={8}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-1" style={{ padding: '20px 24px' }}>
            <Statistic
              title={<span className="cosmic-label">{t('overview.todayApiCalls')}</span>}
              value={overview?.todayLogins || 0}
              valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700 }}
              prefix={<ApiOutlined style={{ color: 'var(--cosmic-cyan-dim)' }} />} />
          </div>
        </Col>
        <Col span={8}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-2" style={{ padding: '20px 24px' }}>
            <Statistic
              title={<span className="cosmic-label">{t('overview.todayErrors')}</span>}
              value={0}
              valueStyle={{ color: 'var(--cosmic-red)', fontWeight: 700 }}
              prefix={<BugOutlined style={{ color: 'rgba(239,68,68,0.3)' }} />} />
          </div>
        </Col>
        <Col span={8}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-3" style={{ padding: '20px 24px' }}>
            <Statistic
              title={<span className="cosmic-label">{t('overview.onlineUsers')}</span>}
              value={overview?.onlineUsers || 0}
              valueStyle={{ color: 'var(--cosmic-green)', fontWeight: 700 }}
              prefix={<TeamOutlined style={{ color: 'rgba(34,197,94,0.3)' }} />} />
          </div>
        </Col>
      </Row>

      {/* ── Pod Status ── */}
      <h3 className="cosmic-heading" style={{ marginTop: 24, marginBottom: 16, fontSize: 15 }}>{t('overview.podStatus')}</h3>
      <div className="glass-panel cosmic-enter cosmic-stagger-4" style={{ padding: 16, overflow: 'auto' }}>
        <Table<PodInfo>
          dataSource={pods}
          rowKey="name"
          size="small"
          pagination={false}
          locale={{ emptyText: t('overview.podLoadFailed') }}>
          <Table.Column<PodInfo> title={t('overview.podName')} dataIndex="name" key="name"
            render={(name: string, record: PodInfo) => (
              <span style={{ fontFamily: 'monospace', fontSize: 13, color: 'var(--cosmic-text-primary)' }}>
                {name}
              </span>
            )} />
          <Table.Column<PodInfo> title={t('overview.podReady')} dataIndex="status" key="status"
            render={(status: string) => (
              <Tag color={status === 'Running' ? 'green' : status === 'Pending' ? 'orange' : 'red'}>
                {status}
              </Tag>
            )} />
          <Table.Column<PodInfo> title={t('overview.podStartedAt')} dataIndex="startTime" key="startTime"
            render={(t: string) => formatK8sTime(t)} />
          <Table.Column<PodInfo> title={t('overview.podRestarts')} dataIndex="restartCount" key="restartCount"
            render={(count: number) => (
              <span style={{ color: count > 0 ? 'var(--cosmic-red)' : 'var(--cosmic-text-secondary)', fontWeight: count > 0 ? 600 : 400 }}>
                {count}
              </span>
            )} />
          <Table.Column<PodInfo> title={t('overview.podLastRestart')} dataIndex="lastRestartTime" key="lastRestartTime"
            render={(t: string | null) => t ? formatK8sTime(t) : '—'} />
        </Table>
      </div>

    </PageLoader>
  );
}

function formatK8sTime(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString();
}

export default MonitorOverviewPage;
