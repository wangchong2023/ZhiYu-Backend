import { useEffect, useState } from 'react';
import { Tabs, Table, Select, Input, Button, Space, Empty } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import apiClient from '../../api/client';
import type { LoginLogDto } from '../../api/types';

interface LogRecord {
  id: number; time: string; level: string; logger: string; message: string; traceId?: string;
}

const LEVEL_COLORS: Record<string, string> = {
  ERROR: '#ff4d4f', WARN: '#faad14', INFO: '#52c41a', DEBUG: '#1677ff', TRACE: '#999',
};

const APP_LOG_COLUMNS: ColumnsType<LogRecord> = [
  { title: '时间', dataIndex: 'time', width: 160 },
  { title: '级别', dataIndex: 'level', width: 80,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#999', fontWeight: 600 }}>{l}</span> },
  { title: 'Logger', dataIndex: 'logger', width: 200, ellipsis: true },
  { title: '消息', dataIndex: 'message', ellipsis: true,
    render: (msg: string) => msg.length > 80 ? msg.slice(0, 80) + '...' : msg },
  { title: 'Trace ID', dataIndex: 'traceId', width: 130, ellipsis: true,
    render: (id?: string) => id ? <a>{id}</a> : '-' },
];

const SECURITY_LOG_COLUMNS: ColumnsType<LoginLogDto> = [
  { title: '时间', dataIndex: 'time', width: 160 },
  { title: '用户', dataIndex: 'username', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100 },
  { title: '类型', dataIndex: 'type', width: 100,
    render: (t: string) => <span style={{ color: LEVEL_COLORS[t] || '#666' }}>{t}</span> },
  { title: '结果', dataIndex: 'result', width: 80 },
  { title: 'IP', dataIndex: 'ip', width: 130 },
  { title: '设备', dataIndex: 'device', ellipsis: true },
];

function MonitorLogsPage() {
  const [activeTab, setActiveTab] = useState('app');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<LogRecord[]>([]);
  const [securityData, setSecurityData] = useState<LoginLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [level, setLevel] = useState<string | undefined>();
  const [type, setType] = useState<string | undefined>();

  const fetchAppLogs = async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/app', {
        params: { page, size: 20, keyword: keyword || undefined, level },
      });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const fetchSecurityLogs = async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/security', {
        params: { page, size: 20, type },
      });
      const body = resp.data?.data;
      setSecurityData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => {
    if (activeTab === 'app') fetchAppLogs();
    else if (activeTab === 'security') fetchSecurityLogs();
  }, [activeTab, page]);

  const handleSearch = () => {
    setPage(1);
    if (activeTab === 'app') fetchAppLogs();
    else if (activeTab === 'security') fetchSecurityLogs();
  };

  const tabItems = [
    {
      key: 'app', label: '应用日志',
      children: (
        <div>
          <Space style={{ marginBottom: 16 }}>
            <Select placeholder="级别" allowClear style={{ width: 120 }} value={level} onChange={setLevel}
              options={['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].map((l) => ({ label: l, value: l }))} />
            <Input placeholder="关键词搜索" value={keyword} onChange={(e) => setKeyword(e.target.value)} style={{ width: 200 }} onPressEnter={handleSearch} />
            <Button icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          </Space>
          <Table columns={APP_LOG_COLUMNS} dataSource={data} rowKey="id" loading={loading} size="small"
            pagination={{ current: page, total, pageSize: 20, onChange: setPage }} />
        </div>
      ),
    },
    {
      key: 'security', label: '安全日志',
      children: (
        <div>
          <Space style={{ marginBottom: 16 }}>
            <Select placeholder="类型" allowClear style={{ width: 120 }} value={type} onChange={setType}
              options={['LOGIN', 'LOGOUT', 'CAPTCHA', 'RATE_LIMIT'].map((l) => ({ label: l, value: l }))} />
            <Button icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          </Space>
          <Table columns={SECURITY_LOG_COLUMNS} dataSource={securityData} rowKey="id" loading={loading} size="small"
            pagination={{ current: page, total, pageSize: 20, onChange: setPage }} />
        </div>
      ),
    },
    { key: 'access', label: '访问日志', children: <Empty description="暂无数据 — 访问日志表尚未创建" /> },
    { key: 'slow-query', label: '慢查询', children: <Empty description="暂无数据 — 慢查询日志表尚未创建" /> },
  ];

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>日志检索</h3>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </div>
  );
}

export default MonitorLogsPage;
