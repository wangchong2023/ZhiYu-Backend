import { useEffect, useState } from 'react';
import { Table, Select, Button, message, Spin, Alert, Space, Popconfirm, Input } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { LoggerDto, LogLevelHistoryDto } from '../../api/types';

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF'];
const LEVEL_COLORS: Record<string, string> = {
  TRACE: '#999', DEBUG: '#1677ff', INFO: '#52c41a', WARN: '#faad14', ERROR: '#ff4d4f', OFF: '#666',
};

function LevelAdjustCell({ logger, onAdjusted }: { logger: LoggerDto; onAdjusted: () => void }) {
  const [selectedLevel, setSelectedLevel] = useState(logger.configuredLevel);
  const [adjusting, setAdjusting] = useState(false);

  const handleAdjust = async () => {
    if (selectedLevel === logger.configuredLevel) return;
    setAdjusting(true);
    try {
      await monitorApi.setLoggerLevel(logger.name, selectedLevel);
      message.success(`已将 ${logger.name} 调整为 ${selectedLevel}`);
      onAdjusted();
    } catch {
      message.error('调整失败');
    } finally {
      setAdjusting(false);
    }
  };

  return (
    <Space>
      <Select size="small" value={selectedLevel} onChange={setSelectedLevel} style={{ width: 100 }}>
        {LEVELS.map((l) => (<Select.Option key={l} value={l}><span style={{ color: LEVEL_COLORS[l] }}>{l}</span></Select.Option>))}
      </Select>
      <Popconfirm title="确认修改日志级别？" onConfirm={handleAdjust} disabled={selectedLevel === logger.configuredLevel}>
        <Button size="small" type="primary" loading={adjusting} disabled={selectedLevel === logger.configuredLevel}>调整</Button>
      </Popconfirm>
    </Space>
  );
}

const LOGGER_COLUMNS: ColumnsType<LoggerDto> = [
  { title: 'Logger 名称', dataIndex: 'name', width: 300, ellipsis: true },
  { title: '当前级别', dataIndex: 'configuredLevel', width: 120,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666', fontWeight: 600 }}>{l}</span> },
  { title: '生效级别', dataIndex: 'effectiveLevel', width: 120,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
  { title: '操作', key: 'action', width: 260,
    render: (_: unknown, record: LoggerDto) => <LevelAdjustCell logger={record} onAdjusted={() => {}} /> },
];

const HISTORY_COLUMNS: ColumnsType<LogLevelHistoryDto> = [
  { title: '时间', dataIndex: 'createdAt', width: 160 },
  { title: 'Logger', dataIndex: 'loggerName', width: 200, ellipsis: true },
  { title: '旧级别', dataIndex: 'oldLevel', width: 80,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
  { title: '新级别', dataIndex: 'newLevel', width: 80,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
  { title: '操作人', dataIndex: 'changedBy', width: 100 },
  { title: '自动回滚', dataIndex: 'expireAt', width: 160,
    render: (t: string | null) => t ? new Date(t).toLocaleString() : '-' },
];

function LogLevelSettingsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loggers, setLoggers] = useState<LoggerDto[]>([]);
  const [history, setHistory] = useState<LogLevelHistoryDto[]>([]);
  const [search, setSearch] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [l, h] = await Promise.all([monitorApi.loggers(), monitorApi.loggerHistory()]);
      setLoggers(l.data?.data || []);
      setHistory(h.data?.data || []);
    } catch {
      setError('加载日志级别数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [refreshKey]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} />;

  const filtered = search
    ? loggers.filter((l) => l.name.toLowerCase().includes(search.toLowerCase()))
    : loggers;

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>日志级别管理</h3>
      <Table columns={LOGGER_COLUMNS} dataSource={filtered.map((l) => ({ ...l, key: l.name }))} rowKey="name" size="small"
        pagination={{ pageSize: 20, showSizeChanger: true }}
        title={() => <Input.Search placeholder="搜索 Logger" value={search} onChange={(e) => setSearch(e.target.value)} style={{ width: 300 }} />} />
      <h4 style={{ marginTop: 24, marginBottom: 12 }}>调整记录</h4>
      <Table columns={HISTORY_COLUMNS} dataSource={history} rowKey="id" size="small" pagination={{ pageSize: 10 }} />
    </div>
  );
}

export default LogLevelSettingsPage;
