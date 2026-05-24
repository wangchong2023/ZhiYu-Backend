import { useEffect, useState, useCallback } from 'react';
import { Table, Select, Button, Space, Tag, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import subscriptionApi from '../../api/subscriptionApi';
import type { SubscriptionDto } from '../../api/types';

const statusColor: Record<string, string> = {
  ACTIVE: 'green', EXPIRED: 'red', CANCELLED: 'default', PENDING: 'orange',
};

function SubscriptionsPage() {
  const { t } = useTranslation();
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
      setError(t('subscription.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, statusFilter, t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const columns = [
    { title: t('common.id'), dataIndex: 'id', width: 80 },
    { title: t('common.username'), dataIndex: 'username' },
    { title: t('subscription.planName'), dataIndex: 'planName' },
    {
      title: t('common.status'), dataIndex: 'status', width: 100,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: t('subscription.startDate'), dataIndex: 'startDate', width: 120,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD') : '-',
    },
    {
      title: t('subscription.endDate'), dataIndex: 'endDate', width: 120,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD') : '-',
    },
    {
      title: t('subscription.autoRenew'), dataIndex: 'autoRenew', width: 100,
      render: (v: number) => v === 1 ? <Tag color="blue">{t('common.yes')}</Tag> : <Tag>{t('common.no')}</Tag>,
    },
    {
      title: t('common.createdAt'), dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder={t('common.status')} allowClear style={{ width: 140 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: t('subscription.statusActive'), value: 'ACTIVE' },
            { label: t('subscription.statusExpired'), value: 'EXPIRED' },
            { label: t('subscription.statusCancelled'), value: 'CANCELLED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
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
