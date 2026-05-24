import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Tag, Select, Row, Col, Statistic } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { AlertDto } from '../../api/types';
import { useAsyncData } from '../../hooks/useAsyncData';
import { PageLoader } from '../../components/PageLoader';
import { STATUS_COLORS } from '../../constants/labels';

const SEVERITY_COLORS: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'gold' };

function MonitorAlertsPage() {
  const { t } = useTranslation();
  const [statusFilter, setStatusFilter] = useState<string>();
  const [severityFilter, setSeverityFilter] = useState<string>();

  const { data: rawAlerts, loading, error, refetch } = useAsyncData<AlertDto[]>(
    () => monitorApi.alerts({ status: statusFilter, severity: severityFilter }),
    [statusFilter, severityFilter],
    t('alerts.loadFailed'),
  );
  const alerts = rawAlerts || [];

  const columns: ColumnsType<AlertDto> = [
    { title: t('alerts.alertName'), dataIndex: 'alertName', width: 160 },
    { title: t('alerts.severity'), dataIndex: 'severity', width: 80,
      render: (s: string) => <Tag color={SEVERITY_COLORS[s] || 'default'}>{s}</Tag> },
    { title: t('alerts.condition'), dataIndex: 'condition', ellipsis: true },
    { title: t('alerts.currentValue'), dataIndex: 'currentValue', width: 140, ellipsis: true },
    { title: t('alerts.status'), dataIndex: 'status', width: 100,
      render: (s: string) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag> },
    { title: t('alerts.firedAt'), dataIndex: 'firedAt', width: 180 },
  ];

  const firingCount = alerts.filter((a) => a.status === 'FIRING').length;
  const resolvedCount = alerts.length - firingCount;

  return (
    <PageLoader loading={loading} error={error} onRetry={refetch}>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-1" style={{ padding: '16px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('alerts.total')}</span>}
              value={alerts.length} valueStyle={{ color: 'var(--cosmic-cyan)', fontWeight: 700 }} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-2" style={{ padding: '16px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('alerts.firing')}</span>}
              value={firingCount} valueStyle={{ color: 'var(--cosmic-red)', fontWeight: 700 }} />
          </div>
        </Col>
        <Col span={6}>
          <div className="glass-panel cosmic-stat-card cosmic-enter cosmic-stagger-3" style={{ padding: '16px 24px' }}>
            <Statistic title={<span className="cosmic-label">{t('alerts.resolved')}</span>}
              value={resolvedCount} valueStyle={{ color: 'var(--cosmic-green)', fontWeight: 700 }} />
          </div>
        </Col>
      </Row>
      <div className="glass-panel" style={{ padding: 16 }}>
        <div style={{ marginBottom: 16 }}>
          <Select placeholder={t('alerts.filterStatus')} allowClear style={{ width: 140, marginRight: 8 }}
            value={statusFilter} onChange={setStatusFilter}
            options={['FIRING', 'RESOLVED'].map((s) => ({ label: s, value: s }))} />
          <Select placeholder={t('alerts.filterSeverity')} allowClear style={{ width: 140 }}
            value={severityFilter} onChange={setSeverityFilter}
            options={['P0', 'P1', 'P2'].map((s) => ({ label: s, value: s }))} />
        </div>
        <Table columns={columns} dataSource={alerts} rowKey="alertName"
          pagination={{ pageSize: 20, showSizeChanger: true }} size="small" />
      </div>
    </PageLoader>
  );
}

export default MonitorAlertsPage;
