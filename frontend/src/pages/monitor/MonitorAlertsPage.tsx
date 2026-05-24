import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Tag, Select, Row, Col, Card, Statistic, Spin, Alert, Button } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { AlertDto } from '../../api/types';

const SEVERITY_COLORS: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'gold' };
const STATUS_COLORS: Record<string, string> = { FIRING: 'red', RESOLVED: 'green' };

function MonitorAlertsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [alerts, setAlerts] = useState<AlertDto[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>();
  const [severityFilter, setSeverityFilter] = useState<string>();

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

  const fetchAlerts = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await monitorApi.alerts({ status: statusFilter, severity: severityFilter });
      setAlerts(resp.data?.data || []);
    } catch {
      setError(t('alerts.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAlerts(); }, [statusFilter, severityFilter]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchAlerts}>{t('common.retry')}</Button>} />;

  const firingCount = alerts.filter((a) => a.status === 'FIRING').length;

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title={t('alerts.total')} value={alerts.length} /></Card></Col>
        <Col span={6}><Card><Statistic title={t('alerts.firing')} value={firingCount} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title={t('alerts.resolved')} value={alerts.length - firingCount} valueStyle={{ color: '#52c41a' }} /></Card></Col>
      </Row>
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
  );
}

export default MonitorAlertsPage;
