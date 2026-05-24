import { useEffect, useState, useCallback } from 'react';
import { Table, Select, Button, Space, Tag, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import subscriptionApi from '../../api/subscriptionApi';
import type { SubscriptionDto } from '../../api/types';

const statusColor: Record<string, string> = {
  ACTIVE: 'green', EXPIRED: 'red', CANCELLED: 'default', PENDING: 'orange',
};

function SubscriptionsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<SubscriptionDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (statusFilter) params.status = statusFilter;
      const res = await subscriptionApi.listSubscriptions(params);
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError('加载订阅列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, size, statusFilter]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '套餐', dataIndex: 'planName' },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: '开始日期', dataIndex: 'startDate', width: 120,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD') : '-',
    },
    {
      title: '结束日期', dataIndex: 'endDate', width: 120,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD') : '-',
    },
    {
      title: '自动续费', dataIndex: 'autoRenew', width: 100,
      render: (v: number) => v === 1 ? <Tag color="blue">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder="状态筛选" allowClear style={{ width: 140 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: '有效', value: 'ACTIVE' },
            { label: '已过期', value: 'EXPIRED' },
            { label: '已取消', value: 'CANCELLED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 900 }} />
    </div>
  );
}

export default SubscriptionsPage;
