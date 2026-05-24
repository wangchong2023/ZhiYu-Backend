import { useEffect, useState, useCallback } from 'react';
import { Table, Select, Button, Space, Tag, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import subscriptionApi from '../../api/subscriptionApi';
import type { PaymentDto } from '../../api/types';

const channelColor: Record<string, string> = {
  WECHAT: 'green', ALIPAY: 'blue', MOCK: 'default',
};
const statusColor: Record<string, string> = {
  PAID: 'green', PENDING: 'orange', FAILED: 'red', REFUNDED: 'purple',
};

function PaymentsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<PaymentDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [channelFilter, setChannelFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (channelFilter) params.channel = channelFilter;
      if (statusFilter) params.status = statusFilter;
      const res = await subscriptionApi.listPayments(params);
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('payment.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, channelFilter, statusFilter, t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const columns = [
    { title: t('common.id'), dataIndex: 'id', width: 80 },
    { title: t('common.username'), dataIndex: 'username' },
    {
      title: t('payment.channel'), dataIndex: 'channel', width: 100,
      render: (c: string) => <Tag color={channelColor[c] || 'default'}>{c}</Tag>,
    },
    { title: t('payment.transactionId'), dataIndex: 'transactionId', ellipsis: true },
    {
      title: t('common.amount'), dataIndex: 'amount', width: 100,
      render: (v: number) => `¥${(v / 100).toFixed(2)}`,
    },
    {
      title: t('common.status'), dataIndex: 'status', width: 80,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: t('payment.paidAt'), dataIndex: 'paidAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder={t('payment.filterChannel')} allowClear style={{ width: 120 }}
          value={channelFilter} onChange={(v) => { setChannelFilter(v); setPage(1); }}
          options={[
            { label: t('payment.channelWechat'), value: 'WECHAT' },
            { label: t('payment.channelAlipay'), value: 'ALIPAY' },
          ]} />
        <Select placeholder={t('payment.filterStatus')} allowClear style={{ width: 100 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: t('payment.statusPaid'), value: 'PAID' },
            { label: t('payment.statusPending'), value: 'PENDING' },
            { label: t('payment.statusRefunded'), value: 'REFUNDED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 800 }} />
    </div>
  );
}

export default PaymentsPage;
