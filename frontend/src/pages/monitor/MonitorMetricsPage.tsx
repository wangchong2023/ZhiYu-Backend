import { useTranslation } from 'react-i18next';
import { Row, Col, Statistic, Progress, Descriptions } from 'antd';
import {
  DashboardOutlined, ThunderboltOutlined, ApartmentOutlined,
  ClockCircleOutlined, DatabaseOutlined,
} from '@ant-design/icons';
import monitorApi from '../../api/monitorApi';
import type { MetricsDto } from '../../api/types';
import { useAsyncData } from '../../hooks/useAsyncData';
import { PageLoader } from '../../components/PageLoader';

function fmtBytes(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(0) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(0) + ' KB';
  return bytes + ' B';
}

function fmtDuration(ms: number): string {
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function MonitorMetricsPage() {
  const { t } = useTranslation();

  const { data: m, loading, error, refetch } = useAsyncData<MetricsDto>(
    () => monitorApi.metrics(),
    [],
    t('metrics.loadFailed'),
  );

  const cpuPercent = m ? Math.round(m.processCpuLoad * 100) : 0;
  const sysCpuPercent = m ? Math.round(m.systemCpuLoad * 100) : 0;
  const heapPercent = m && m.heapMaxBytes > 0 ? Math.round((m.heapUsedBytes / m.heapMaxBytes) * 100) : 0;
  const sysMemUsed = m ? m.systemMemoryTotal - m.systemMemoryFree : 0;
  const sysMemPercent = m && m.systemMemoryTotal > 0 ? Math.round((sysMemUsed / m.systemMemoryTotal) * 100) : 0;

  return (
    <PageLoader loading={loading} error={error} onRetry={refetch}>
      {/* ── CPU ── */}
      <h3 className="cosmic-heading" style={{ marginBottom: 16, fontSize: 15 }}>
        <DashboardOutlined style={{ marginRight: 8 }} />{t('metrics.cpu')}
      </h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={4}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '16px 20px', textAlign: 'center' }}>
            <Progress type="dashboard" percent={cpuPercent} size={100}
              strokeColor="var(--cosmic-cyan)" format={() => cpuPercent + '%'} />
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--cosmic-text-muted)' }}>
              {t('metrics.processCpu')}
            </div>
          </div>
        </Col>
        <Col span={4}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '16px 20px', textAlign: 'center' }}>
            <Progress type="dashboard" percent={sysCpuPercent} size={100}
              strokeColor="var(--cosmic-amber)" format={() => sysCpuPercent + '%'} />
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--cosmic-text-muted)' }}>
              {t('metrics.systemCpu')}
            </div>
          </div>
        </Col>
        <Col span={4}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '20px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('metrics.cpuCores')}</span>}
              value={m?.cpuCores ?? '-'} prefix={<ApartmentOutlined />}
              valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700, fontSize: 22 }} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '20px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('metrics.threads')}</span>}
              value={m ? `${m.threadCount} / ${m.peakThreadCount}` : '-'}
              valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700, fontSize: 20 }} />
            <div style={{ fontSize: 11, color: 'var(--cosmic-text-muted)' }}>{t('metrics.currentVsPeak')}</div>
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '20px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('metrics.uptime')}</span>}
              value={m ? fmtDuration(m.processUptimeMs) : '-'} prefix={<ClockCircleOutlined />}
              valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700, fontSize: 22 }} />
          </div>
        </Col>
      </Row>

      {/* ── Memory ── */}
      <h3 className="cosmic-heading" style={{ marginBottom: 16, fontSize: 15 }}>
        <DatabaseOutlined style={{ marginRight: 8 }} />{t('metrics.memory')}
      </h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={4}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '16px 20px', textAlign: 'center' }}>
            <Progress type="dashboard" percent={heapPercent} size={100}
              strokeColor="var(--cosmic-green)" format={() => heapPercent + '%'} />
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--cosmic-text-muted)' }}>
              {t('metrics.heapUsage')}
            </div>
          </div>
        </Col>
        <Col span={4}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '16px 20px', textAlign: 'center' }}>
            <Progress type="dashboard" percent={sysMemPercent} size={100}
              strokeColor="var(--cosmic-purple)" format={() => sysMemPercent + '%'} />
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--cosmic-text-muted)' }}>
              {t('metrics.systemMemory')}
            </div>
          </div>
        </Col>
        <Col span={16}>
          <div className="glass-panel cosmic-stat-card" style={{ padding: '20px 24px' }}>
            <Descriptions column={2} size="small" colon={false}
              labelStyle={{ color: 'var(--cosmic-text-muted)', fontSize: 12 }}
              contentStyle={{ color: 'var(--cosmic-text)', fontSize: 13, fontWeight: 600 }}>
              <Descriptions.Item label={t('metrics.processRss')}>
                {m ? fmtBytes(m.rssBytes) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('metrics.heapUsed')}>
                {m ? fmtBytes(m.heapUsedBytes) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('metrics.totalMemory')}>
                {m ? fmtBytes(m.systemMemoryTotal) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('metrics.heapMax')}>
                {m ? fmtBytes(m.heapMaxBytes) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('metrics.freeMemory')}>
                {m ? fmtBytes(m.systemMemoryFree) : '-'}
              </Descriptions.Item>
            </Descriptions>
          </div>
        </Col>
      </Row>
    </PageLoader>
  );
}

export default MonitorMetricsPage;
