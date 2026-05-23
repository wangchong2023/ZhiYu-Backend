import { useEffect, useState } from 'react';
import { Table, Tag, Select, Row, Col, Card, Statistic, Spin, Alert, Button } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { AlertDto } from '../../api/types';

const SEVERITY_COLORS: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'gold' };
const STATUS_COLORS: Record<string, string> = { FIRING: 'red', RESOLVED: 'green' };

const COLUMNS: ColumnsType<AlertDto> = [
  { title: '告警名称', dataIndex: 'alertName', width: 160 },
  { title: '级别', dataIndex: 'severity', width: 80,
    render: (s: string) => <Tag color={SEVERITY_COLORS[s] || 'default'}>{s}</Tag> },
  { title: '条件', dataIndex: 'condition', ellipsis: true },
  { title: '当前值', dataIndex: 'currentValue', width: 140, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 100,
    render: (s: string) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag> },
  { title: '触发时间', dataIndex: 'firedAt', width: 180 },
];

function MonitorAlertsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [alerts, setAlerts] = useState<AlertDto[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>();
  const [severityFilter, setSeverityFilter] = useState<string>();

  const fetchAlerts = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await monitorApi.alerts({ status: statusFilter, severity: severityFilter });
      setAlerts(resp.data?.data || []);
    } catch {
      setError('加载告警数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAlerts(); }, [statusFilter, severityFilter]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchAlerts}>重试</Button>} />;

  const firingCount = alerts.filter((a) => a.status === 'FIRING').length;

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="告警总数" value={alerts.length} /></Card></Col>
        <Col span={6}><Card><Statistic title="FIRING" value={firingCount} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="RESOLVED" value={alerts.length - firingCount} valueStyle={{ color: '#52c41a' }} /></Card></Col>
      </Row>
      <div style={{ marginBottom: 16 }}>
        <Select placeholder="状态筛选" allowClear style={{ width: 140, marginRight: 8 }}
          value={statusFilter} onChange={setStatusFilter}
          options={['FIRING', 'RESOLVED'].map((s) => ({ label: s, value: s }))} />
        <Select placeholder="级别筛选" allowClear style={{ width: 140 }}
          value={severityFilter} onChange={setSeverityFilter}
          options={['P0', 'P1', 'P2'].map((s) => ({ label: s, value: s }))} />
      </div>
      <Table columns={COLUMNS} dataSource={alerts} rowKey="alertName"
        pagination={{ pageSize: 20, showSizeChanger: true }} size="small" />
    </div>
  );
}

export default MonitorAlertsPage;
