import { useEffect, useState, useCallback } from 'react';
import {
  Table, Select, Button, Space, Tag, DatePicker, Tabs,
  Alert, TablePaginationConfig, Input,
} from 'antd';
import { ReloadOutlined, DownloadOutlined } from '@ant-design/icons';
import type { AdminOperationDto } from '../../api/types';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import apiClient from '../../api/client';

const DEFAULT_PAGE_SIZE = 20;

interface LoginLogDto {
  id: number;
  username: string;
  action: string;
  type: string;
  result: string;
  ip: string;
  device: string;
  location: string;
  time: string;
}

interface IdentityChangeDto {
  id: number;
  userId: number;
  action: string;
  identityType: string;
  sourceIp: string;
  createdAt: string;
}

const { RangePicker } = DatePicker;

const resultColor: Record<string, string> = {
  SUCCESS: 'green', FAILURE: 'red', LOCKED: 'orange',
};

function useResultLabel(t: (key: string) => string): Record<string, string> {
  return {
    SUCCESS: t('audit.success'),
    FAILURE: t('audit.failure'),
    LOCKED: t('audit.locked'),
  };
}

function LoginLogTab() {
  const { t } = useTranslation();
  const resultLabel = useResultLabel(t);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<LoginLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [resultFilter, setResultFilter] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);

  const typeLabels: Record<string, string> = {
    PASSWORD: t('label.password'),
    WECHAT: t('label.wechat'), APPLE: t('label.apple'),
    GOOGLE: t('label.google'), WEBAUTHN: t('label.passkey'),
  };

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (typeFilter) params.type = typeFilter;
      if (resultFilter) params.result = resultFilter;
      if (dateRange) {
        params.startTime = dateRange[0].startOf('day').toISOString();
        params.endTime = dateRange[1].endOf('day').toISOString();
      }
      const res = await apiClient.get('/admin/logs/login', { params });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('audit.loadLoginFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, typeFilter, resultFilter, dateRange, t]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current) setPage(pag.current);
    if (pag.pageSize) setSize(pag.pageSize);
  };

  const handleExport = async () => {
    try {
      const params: Record<string, unknown> = { page: 1, size: 10000 };
      if (typeFilter) params.type = typeFilter;
      if (resultFilter) params.result = resultFilter;
      if (dateRange) {
        params.startTime = dateRange[0].startOf('day').toISOString();
        params.endTime = dateRange[1].endOf('day').toISOString();
      }
      const res = await apiClient.get('/admin/logs/login', { params });
      const rows = (res.data?.data?.records || []) as LoginLogDto[];
      const csv = [
        ['ID', t('audit.username'), t('audit.action'), t('audit.method'), t('audit.result'), t('audit.ip'), t('audit.device'), t('audit.location'), t('audit.time')].join(','),
        ...rows.map((r) => [
          r.id, r.username, r.action, r.type, r.result, r.ip, r.device, r.location, r.time,
        ].join(',')),
      ].join('\n');
      const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `audit-log-${dayjs().format('YYYYMMDD')}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      // ignore export errors
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: t('audit.username'), dataIndex: 'username', width: 120 },
    { title: t('audit.action'), dataIndex: 'action', width: 80 },
    {
      title: t('audit.method'), dataIndex: 'type', width: 100,
      render: (v: string) => <Tag>{typeLabels[v] || v}</Tag>,
    },
    {
      title: t('audit.result'), dataIndex: 'result', width: 80,
      render: (v: string) => <Tag color={resultColor[v] || 'default'}>{resultLabel[v] || v}</Tag>,
    },
    { title: t('audit.ip'), dataIndex: 'ip', width: 140 },
    { title: t('audit.device'), dataIndex: 'device', ellipsis: true, width: 100 },
    { title: t('audit.location'), dataIndex: 'location', width: 100 },
    { title: t('audit.time'), dataIndex: 'time', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select placeholder={t('audit.filterLoginMethod')} allowClear style={{ width: 120 }}
          value={typeFilter}
          onChange={(v) => { setTypeFilter(v); setPage(1); }}
          options={[
            { label: t('label.password'), value: 'PASSWORD' },
            { label: t('label.wechat'), value: 'WECHAT' },
            { label: t('label.apple'), value: 'APPLE' },
            { label: t('label.google'), value: 'GOOGLE' },
            { label: t('label.passkey'), value: 'WEBAUTHN' },
          ]} />
        <Select placeholder={t('audit.filterResult')} allowClear style={{ width: 100 }}
          value={resultFilter}
          onChange={(v) => { setResultFilter(v); setPage(1); }}
          options={[
            { label: t('audit.success'), value: 'SUCCESS' },
            { label: t('audit.failure'), value: 'FAILURE' },
            { label: t('audit.locked'), value: 'LOCKED' },
          ]} />
        <RangePicker
          value={dateRange as [dayjs.Dayjs, dayjs.Dayjs] | null}
          onChange={(dates) => {
            setDateRange(dates ? [dates[0]!, dates[1]!] : null);
            setPage(1);
          }}
        />
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>{t('common.refresh')}</Button>
        <Button icon={<DownloadOutlined />} onClick={handleExport}>{t('audit.exportCsv')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchLogs}>{t('common.retry')}</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 1000 }} />
    </div>
  );
}

function IdentityChangeTab() {
  const { t } = useTranslation();

  const providerLabels: Record<string, { label: string; color: string }> = {
    PASSWORD: { label: t('label.password'), color: 'default' },
    WECHAT: { label: t('label.wechat'), color: 'green' },
    APPLE: { label: t('label.apple'), color: 'default' },
    GOOGLE: { label: t('label.google'), color: 'blue' },
    WEBAUTHN: { label: t('label.passkey'), color: 'purple' },
  };
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<IdentityChangeDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [userIdFilter, setUserIdFilter] = useState('');
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);

  const fetchChanges = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (userIdFilter) params.userId = userIdFilter;
      if (dateRange) {
        params.start = dateRange[0].startOf('day').toISOString();
        params.end = dateRange[1].endOf('day').toISOString();
      }
      const res = await apiClient.get('/admin/audit/identity-changes', { params });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('audit.loadIdentityFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, userIdFilter, dateRange, t]);

  useEffect(() => { fetchChanges(); }, [fetchChanges]);

  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current) setPage(pag.current);
    if (pag.pageSize) setSize(pag.pageSize);
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: t('audit.userId'), dataIndex: 'userId', width: 100 },
    {
      title: t('audit.action'), dataIndex: 'action', width: 80,
      render: (v: string) => (
        <Tag color={v === 'BIND' ? 'green' : 'red'}>
          {v === 'BIND' ? t('audit.bind') : t('audit.unbind')}
        </Tag>
      ),
    },
    {
      title: t('audit.authType'), dataIndex: 'identityType', width: 100,
      render: (v: string) => {
        const info = providerLabels[v] || { label: v, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    { title: t('audit.ip'), dataIndex: 'sourceIp', width: 140 },
    {
      title: t('audit.time'), dataIndex: 'createdAt', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder={t('audit.userId')} allowClear style={{ width: 120 }}
          value={userIdFilter}
          onChange={(e) => { setUserIdFilter(e.target.value); setPage(1); }}
        />
        <RangePicker
          value={dateRange as [dayjs.Dayjs, dayjs.Dayjs] | null}
          onChange={(dates) => {
            setDateRange(dates ? [dates[0]!, dates[1]!] : null);
            setPage(1);
          }}
        />
        <Button icon={<ReloadOutlined />} onClick={fetchChanges}>{t('common.refresh')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchChanges}>{t('common.retry')}</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 700 }} />
    </div>
  );
}

const actionLabels: Record<string, string> = {
  CREATE_USER: 'audit.actionCreateUser',
  ENABLE_USER: 'audit.actionEnableUser',
  DISABLE_USER: 'audit.actionDisableUser',
  DELETE_USER: 'audit.actionDeleteUser',
  UPDATE_USER: 'audit.actionUpdateUser',
  RESET_PASSWORD: 'audit.actionResetPassword',
  ADJUST_LOG_LEVEL: 'audit.actionAdjustLogLevel',
};

const actionColors: Record<string, string> = {
  CREATE_USER: 'green',
  ENABLE_USER: 'blue',
  DISABLE_USER: 'orange',
  DELETE_USER: 'red',
  UPDATE_USER: 'blue',
  RESET_PASSWORD: 'orange',
  ADJUST_LOG_LEVEL: 'purple',
};

function AdminOperationTab() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminOperationDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [usernameFilter, setUsernameFilter] = useState('');
  const [actionFilter, setActionFilter] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);

  const fetchOperations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (usernameFilter) params.username = usernameFilter;
      if (actionFilter) params.action = actionFilter;
      if (dateRange) {
        params.startTime = dateRange[0].startOf('day').toISOString();
        params.endTime = dateRange[1].endOf('day').toISOString();
      }
      const res = await apiClient.get('/admin/audit/admin-operations', { params });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('audit.loadAdminOpFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, usernameFilter, actionFilter, dateRange, t]);

  useEffect(() => { fetchOperations(); }, [fetchOperations]);

  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current) setPage(pag.current);
    if (pag.pageSize) setSize(pag.pageSize);
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: t('audit.operator'), dataIndex: 'username', width: 120 },
    {
      title: t('audit.action'), dataIndex: 'action', width: 110,
      render: (v: string) => (
        <Tag color={actionColors[v] || 'default'}>
          {t(actionLabels[v] || v)}
        </Tag>
      ),
    },
    { title: t('audit.target'), dataIndex: 'target', width: 120 },
    { title: t('audit.ip'), dataIndex: 'ip', width: 140 },
    {
      title: t('audit.time'), dataIndex: 'time', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder={t('audit.filterOperator')} allowClear style={{ width: 140 }}
          value={usernameFilter}
          onChange={(e) => { setUsernameFilter(e.target.value); setPage(1); }}
        />
        <Select placeholder={t('audit.filterAction')} allowClear style={{ width: 140 }}
          value={actionFilter}
          onChange={(v) => { setActionFilter(v); setPage(1); }}
          options={Object.entries(actionLabels).map(([value, labelKey]) => ({ label: t(labelKey), value }))}
        />
        <RangePicker
          value={dateRange as [dayjs.Dayjs, dayjs.Dayjs] | null}
          onChange={(dates) => {
            setDateRange(dates ? [dates[0]!, dates[1]!] : null);
            setPage(1);
          }}
        />
        <Button icon={<ReloadOutlined />} onClick={fetchOperations}>{t('common.refresh')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchOperations}>{t('common.retry')}</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 800 }} />
    </div>
  );
}

function AuditLogPage() {
  const { t } = useTranslation();
  return (
    <Tabs
      defaultActiveKey="login"
      items={[
        { key: 'login', label: t('audit.loginLog'), children: <LoginLogTab /> },
        { key: 'identity', label: t('audit.identityChange'), children: <IdentityChangeTab /> },
        { key: 'admin-ops', label: t('audit.adminOperation'), children: <AdminOperationTab /> },
      ]}
    />
  );
}

export default AuditLogPage;
