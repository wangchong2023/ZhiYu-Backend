import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Table, Select, Button, message, Spin, Alert, Space, Popconfirm, Input } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { LoggerDto, LogLevelHistoryDto } from '../../api/types';

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF'];
const LEVEL_COLORS: Record<string, string> = {
  TRACE: '#999', DEBUG: '#1677ff', INFO: '#52c41a', WARN: '#faad14', ERROR: '#ff4d4f', OFF: '#666',
};

function LevelAdjustCell({ logger, onAdjusted }: { logger: LoggerDto; onAdjusted: () => void }) {
  const { t } = useTranslation();
  const [selectedLevel, setSelectedLevel] = useState(logger.configuredLevel);
  const [adjusting, setAdjusting] = useState(false);

  const handleAdjust = async () => {
    if (selectedLevel === logger.configuredLevel) return;
    setAdjusting(true);
    try {
      await monitorApi.setLoggerLevel(logger.name, selectedLevel);
      message.success(t('logLevel.adjustSuccess'));
      onAdjusted();
    } catch {
      message.error(t('logLevel.adjustFailed'));
    } finally {
      setAdjusting(false);
    }
  };

  return (
    <Space>
      <Select size="small" value={selectedLevel} onChange={setSelectedLevel} style={{ width: 100 }}>
        {LEVELS.map((l) => (<Select.Option key={l} value={l}><span style={{ color: LEVEL_COLORS[l] }}>{l}</span></Select.Option>))}
      </Select>
      <Popconfirm title={t('logLevel.confirmTitle')} onConfirm={handleAdjust} disabled={selectedLevel === logger.configuredLevel}>
        <Button size="small" type="primary" loading={adjusting} disabled={selectedLevel === logger.configuredLevel}>{t('logLevel.adjust')}</Button>
      </Popconfirm>
    </Space>
  );
}

function LogLevelSettingsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loggers, setLoggers] = useState<LoggerDto[]>([]);
  const [history, setHistory] = useState<LogLevelHistoryDto[]>([]);
  const [search, setSearch] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const loggerColumns: ColumnsType<LoggerDto> = [
    { title: t('logLevel.loggerName'), dataIndex: 'name', width: 300, ellipsis: true },
    { title: t('logLevel.effectiveLevel'), dataIndex: 'configuredLevel', width: 120,
      render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666', fontWeight: 600 }}>{l}</span> },
    { title: t('logLevel.effectiveLevel'), dataIndex: 'effectiveLevel', width: 120,
      render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
    { title: t('logLevel.action'), key: 'action', width: 260,
      render: (_: unknown, record: LoggerDto) => <LevelAdjustCell logger={record} onAdjusted={() => setRefreshKey((k) => k + 1)} /> },
  ];

  const historyColumns: ColumnsType<LogLevelHistoryDto> = [
    { title: t('logs.time'), dataIndex: 'createdAt', width: 160 },
    { title: t('logs.logger'), dataIndex: 'loggerName', width: 200, ellipsis: true },
    { title: t('logLevel.historyFrom'), dataIndex: 'oldLevel', width: 80,
      render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
    { title: t('logLevel.historyTo'), dataIndex: 'newLevel', width: 80,
      render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
    { title: t('logLevel.changedBy'), dataIndex: 'changedBy', width: 100 },
    { title: t('logLevel.autoRollback'), dataIndex: 'expireAt', width: 160,
      render: (v: string | null) => v ? new Date(v).toLocaleString() : '-' },
  ];

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [l, h] = await Promise.all([monitorApi.loggers(), monitorApi.loggerHistory()]);
      setLoggers(l.data?.data || []);
      setHistory(h.data?.data || []);
    } catch {
      setError(t('logLevel.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [refreshKey]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} />;

  const filtered = search
    ? loggers.filter((l) => l.name.toLowerCase().includes(search.toLowerCase()))
    : loggers;

  return (
    <div>
      <h3 className="cosmic-heading" style={{ marginBottom: 16, fontSize: 15 }}>{t('logLevel.title')}</h3>
      <Table columns={loggerColumns} dataSource={filtered.map((l) => ({ ...l, key: l.name }))} rowKey="name" size="small"
        pagination={{ pageSize: 20, showSizeChanger: true }}
        title={() => <Input.Search placeholder={t('logLevel.searchPlaceholder')} value={search} onChange={(e) => setSearch(e.target.value)} style={{ width: 300 }} />} />
      <h4 className="cosmic-heading" style={{ marginTop: 24, marginBottom: 12, fontSize: 14 }}>{t('logLevel.adjustHistory')}</h4>
      <Table columns={historyColumns} dataSource={history} rowKey="id" size="small" pagination={{ pageSize: 10 }} />
    </div>
  );
}

export default LogLevelSettingsPage;
