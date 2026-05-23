import { useEffect, useState, useCallback } from 'react';
import {
  Table, Select, Button, Space, Tag, DatePicker,
  Spin, Alert, TablePaginationConfig,
} from 'antd';
import { ReloadOutlined, DownloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import apiClient from '../../api/client';

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

const { RangePicker } = DatePicker;

const resultColor: Record<string, string> = {
  SUCCESS: 'green', FAILURE: 'red', LOCKED: 'orange',
};

function AuditLogPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<LoginLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [resultFilter, setResultFilter] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);

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
      setError('加载审计日志失败');
    } finally {
      setLoading(false);
    }
  }, [page, size, typeFilter, resultFilter, dateRange]);

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
        ['ID', '用户', '操作', '方式', '结果', 'IP', '设备', '位置', '时间'].join(','),
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
    { title: '用户', dataIndex: 'username', width: 120 },
    { title: '操作', dataIndex: 'action', width: 80 },
    {
      title: '方式', dataIndex: 'type', width: 100,
      render: (v: string) => {
        const labels: Record<string, string> = {
          PASSWORD: '密码', WECHAT: '微信', APPLE: 'Apple',
          GOOGLE: 'Google', WEBAUTHN: '通行密钥',
        };
        return <Tag>{labels[v] || v}</Tag>;
      },
    },
    {
      title: '结果', dataIndex: 'result', width: 80,
      render: (v: string) => <Tag color={resultColor[v] || 'default'}>{v}</Tag>,
    },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '设备', dataIndex: 'device', ellipsis: true, width: 100 },
    { title: '位置', dataIndex: 'location', width: 100 },
    { title: '时间', dataIndex: 'time', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select placeholder="登录方式" allowClear style={{ width: 120 }}
          value={typeFilter}
          onChange={(v) => { setTypeFilter(v); setPage(1); }}
          options={[
            { label: '密码', value: 'PASSWORD' },
            { label: '微信', value: 'WECHAT' },
            { label: 'Apple', value: 'APPLE' },
            { label: 'Google', value: 'GOOGLE' },
            { label: '通行密钥', value: 'WEBAUTHN' },
          ]} />
        <Select placeholder="结果" allowClear style={{ width: 100 }}
          value={resultFilter}
          onChange={(v) => { setResultFilter(v); setPage(1); }}
          options={[
            { label: '成功', value: 'SUCCESS' },
            { label: '失败', value: 'FAILURE' },
            { label: '锁定', value: 'LOCKED' },
          ]} />
        <RangePicker
          value={dateRange as [dayjs.Dayjs, dayjs.Dayjs] | null}
          onChange={(dates) => {
            setDateRange(dates ? [dates[0]!, dates[1]!] : null);
            setPage(1);
          }}
        />
        <Button icon={<ReloadOutlined />} onClick={fetchLogs}>刷新</Button>
        <Button icon={<DownloadOutlined />} onClick={handleExport}>导出 CSV</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchLogs}>重试</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 1000 }} />
    </div>
  );
}

export default AuditLogPage;
