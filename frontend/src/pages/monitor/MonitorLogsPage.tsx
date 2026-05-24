import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Tabs, Table, Select, Input, Button, Space, InputNumber } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import apiClient from '../../api/client';
import type { LoginLogDto, AccessLogDto, SlowQueryDto } from '../../api/types';

interface LogRecord {
  id: number; time: string; level: string; logger: string; message: string; traceId?: string;
}

const LEVEL_COLORS: Record<string, string> = {
  ERROR: '#ff4d4f', WARN: '#faad14', INFO: '#52c41a', DEBUG: '#1677ff', TRACE: '#999',
};

const STATUS_COLORS: Record<number, string> = {
  2: '#52c41a', 3: '#1677ff', 4: '#faad14', 5: '#ff4d4f',
};

const DEFAULT_PAGE_SIZE = 20;

function AppLogTab() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<LogRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [level, setLevel] = useState<string | undefined>();

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/app', {
        params: { page, size: DEFAULT_PAGE_SIZE, keyword: keyword || undefined, level },
      });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [page, keyword, level]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const columns: ColumnsType<LogRecord> = [
    { title: t('logs.time'), dataIndex: 'time', width: 160 },
    { title: t('logs.level'), dataIndex: 'level', width: 80,
      render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#999', fontWeight: 600 }}>{l}</span> },
    { title: t('logs.logger'), dataIndex: 'logger', width: 200, ellipsis: true },
    { title: t('logs.message'), dataIndex: 'message', ellipsis: true },
    { title: t('logs.traceId'), dataIndex: 'traceId', width: 130, ellipsis: true,
      render: (id?: string) => id ? <a>{id}</a> : '-' },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder={t('logs.level')} allowClear style={{ width: 120 }} value={level}
          onChange={(v) => { setLevel(v); setPage(1); }}
          options={['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].map((l) => ({ label: l, value: l }))} />
        <Input placeholder={t('logs.keyword')} value={keyword}
          onChange={(e) => setKeyword(e.target.value)} style={{ width: 200 }}
          onPressEnter={() => { setPage(1); fetchLogs(); }} />
        <Button icon={<SearchOutlined />} onClick={() => { setPage(1); fetchLogs(); }}>{t('logs.search')}</Button>
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>{t('common.refresh')}</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} size="small"
        pagination={{ current: page, total, pageSize: DEFAULT_PAGE_SIZE, onChange: setPage }} />
    </div>
  );
}

function SecurityLogTab() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<LoginLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [type, setType] = useState<string | undefined>();

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/security', {
        params: { page, size: DEFAULT_PAGE_SIZE, type },
      });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [page, type]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const columns: ColumnsType<LoginLogDto> = [
    { title: t('logs.time'), dataIndex: 'time', width: 160 },
    { title: t('logs.user'), dataIndex: 'username', width: 120 },
    { title: t('logs.action'), dataIndex: 'action', width: 100 },
    { title: t('logs.type'), dataIndex: 'type', width: 100,
      render: (tp: string) => <span style={{ color: LEVEL_COLORS[tp] || '#666' }}>{tp}</span> },
    { title: t('logs.result'), dataIndex: 'result', width: 80 },
    { title: t('logs.ip'), dataIndex: 'ip', width: 130 },
    { title: t('logs.device'), dataIndex: 'device', ellipsis: true },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder={t('logs.type')} allowClear style={{ width: 120 }} value={type}
          onChange={(v) => { setType(v); setPage(1); }}
          options={['LOGIN', 'LOGOUT', 'CAPTCHA', 'RATE_LIMIT'].map((l) => ({ label: l, value: l }))} />
        <Button icon={<SearchOutlined />} onClick={() => { setPage(1); fetchLogs(); }}>{t('logs.search')}</Button>
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>{t('common.refresh')}</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} size="small"
        pagination={{ current: page, total, pageSize: DEFAULT_PAGE_SIZE, onChange: setPage }} />
    </div>
  );
}

function AccessLogTab() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<AccessLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [ipFilter, setIpFilter] = useState('');
  const [methodFilter, setMethodFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page, size: DEFAULT_PAGE_SIZE };
      if (ipFilter) params.ip = ipFilter;
      if (methodFilter) params.method = methodFilter;
      if (statusFilter) params.statusCode = statusFilter;
      const resp = await apiClient.get('/admin/logs/access', { params });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [page, ipFilter, methodFilter, statusFilter]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const columns: ColumnsType<AccessLogDto> = [
    { title: t('logs.time'), dataIndex: 'time', width: 160 },
    { title: t('logs.ip'), dataIndex: 'ip', width: 140 },
    { title: t('logs.method'), dataIndex: 'method', width: 80,
      render: (m: string) => {
        const colors: Record<string, string> = { GET: '#52c41a', POST: '#1677ff', PUT: '#faad14', DELETE: '#ff4d4f' };
        return <span style={{ color: colors[m] || '#666', fontWeight: 600 }}>{m}</span>;
      },
    },
    { title: t('logs.path'), dataIndex: 'path', ellipsis: true },
    { title: t('logs.statusCode'), dataIndex: 'statusCode', width: 80,
      render: (c: number) => {
        const cls = Math.floor(c / 100);
        return <span style={{ color: STATUS_COLORS[cls] || '#999', fontWeight: 600 }}>{c}</span>;
      },
    },
    { title: t('logs.responseTime'), dataIndex: 'responseTimeMs', width: 100,
      render: (ms: number) => `${ms} ms`,
    },
    { title: t('logs.userAgent'), dataIndex: 'userAgent', width: 200, ellipsis: true },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input placeholder={t('logs.filterIp')} allowClear style={{ width: 160 }}
          value={ipFilter} onChange={(e) => { setIpFilter(e.target.value); setPage(1); }} />
        <Select placeholder={t('logs.filterMethod')} allowClear style={{ width: 100 }}
          value={methodFilter} onChange={(v) => { setMethodFilter(v); setPage(1); }}
          options={['GET', 'POST', 'PUT', 'DELETE'].map((m) => ({ label: m, value: m }))} />
        <Select placeholder={t('logs.filterStatusCode')} allowClear style={{ width: 100 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: '2xx', value: '2xx' },
            { label: '3xx', value: '3xx' },
            { label: '4xx', value: '4xx' },
            { label: '5xx', value: '5xx' },
          ]} />
        <Button icon={<SearchOutlined />} onClick={() => { setPage(1); fetchLogs(); }}>{t('logs.search')}</Button>
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>{t('common.refresh')}</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} size="small"
        pagination={{ current: page, total, pageSize: DEFAULT_PAGE_SIZE, onChange: setPage }} scroll={{ x: 900 }} />
    </div>
  );
}

const MIN_SLOW_QUERY_MS = 100;

function SlowQueryTab() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<SlowQueryDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [minDuration, setMinDuration] = useState<number | null>(MIN_SLOW_QUERY_MS);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page, size: DEFAULT_PAGE_SIZE };
      if (minDuration !== null) params.minDurationMs = minDuration;
      const resp = await apiClient.get('/admin/logs/slow-query', { params });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  }, [page, minDuration]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const columns: ColumnsType<SlowQueryDto> = [
    { title: t('logs.time'), dataIndex: 'time', width: 160 },
    { title: t('logs.sqlSummary'), dataIndex: 'sqlSummary', ellipsis: true },
    { title: t('logs.duration'), dataIndex: 'durationMs', width: 100,
      render: (ms: number) => (
        <span style={{ color: ms > 1000 ? '#ff4d4f' : ms > 500 ? '#faad14' : '#52c41a', fontWeight: 600 }}>
          {ms} ms
        </span>
      ),
    },
    { title: t('logs.source'), dataIndex: 'source', width: 160, ellipsis: true },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <span>{t('logs.filterMinDuration')}</span>
        <InputNumber min={0} style={{ width: 120 }} value={minDuration}
          onChange={(v) => { setMinDuration(v); setPage(1); }} />
        <Button icon={<SearchOutlined />} onClick={() => { setPage(1); fetchLogs(); }}>{t('logs.search')}</Button>
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>{t('common.refresh')}</Button>
      </Space>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} size="small"
        pagination={{ current: page, total, pageSize: DEFAULT_PAGE_SIZE, onChange: setPage }} scroll={{ x: 700 }} />
    </div>
  );
}

function MonitorLogsPage() {
  const { t } = useTranslation();

  const tabItems = [
    { key: 'app', label: t('logs.appLog'), children: <AppLogTab /> },
    { key: 'security', label: t('logs.securityLog'), children: <SecurityLogTab /> },
    { key: 'access', label: t('logs.accessLog'), children: <AccessLogTab /> },
    { key: 'slow-query', label: t('logs.slowQuery'), children: <SlowQueryTab /> },
  ];

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>{t('logs.title')}</h3>
      <Tabs defaultActiveKey="app" items={tabItems} />
    </div>
  );
}

export default MonitorLogsPage;
