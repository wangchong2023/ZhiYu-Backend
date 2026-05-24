import { useEffect, useState, useCallback } from 'react';
import { Table, Select, Button, Space, Tag, Modal, Input, message, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import subscriptionApi from '../../api/subscriptionApi';
import type { RefundDto, RefundReviewRequest } from '../../api/types';

const statusColor: Record<string, string> = {
  PENDING_REVIEW: 'orange', APPROVED: 'green', REJECTED: 'red', REFUNDED: 'blue',
};

function RefundsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<RefundDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [reviewModal, setReviewModal] = useState<{ open: boolean; refund: RefundDto | null; action: 'approve' | 'reject' }>({ open: false, refund: null, action: 'approve' });
  const [reviewNote, setReviewNote] = useState('');

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (statusFilter) params.status = statusFilter;
      const res = await subscriptionApi.listRefunds(params);
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('refund.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, statusFilter, t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleReview = async () => {
    if (!reviewModal.refund) return;
    const req: RefundReviewRequest = { decision: reviewModal.action === 'approve' ? 'APPROVED' : 'REJECTED', note: reviewNote };
    try {
      if (reviewModal.action === 'approve') {
        await subscriptionApi.approveRefund(reviewModal.refund.id, req);
      } else {
        await subscriptionApi.rejectRefund(reviewModal.refund.id, req);
      }
      message.success(reviewModal.action === 'approve' ? t('refund.approveSuccess') : t('refund.rejectSuccess'));
      setReviewModal({ open: false, refund: null, action: 'approve' });
      setReviewNote('');
      fetchData();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const openReview = (refund: RefundDto, action: 'approve' | 'reject') => {
    setReviewModal({ open: true, refund, action });
    setReviewNote('');
  };

  const columns = [
    { title: t('refund.refundNo'), dataIndex: 'refundNo', width: 140 },
    { title: t('common.username'), dataIndex: 'username' },
    { title: t('refund.orderNo'), dataIndex: 'orderNo', width: 140 },
    {
      title: t('common.amount'), dataIndex: 'amount', width: 100,
      render: (v: number) => `¥${(v / 100).toFixed(2)}`,
    },
    { title: t('refund.reason'), dataIndex: 'reason', ellipsis: true },
    {
      title: t('common.status'), dataIndex: 'status', width: 110,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: t('refund.appliedAt'), dataIndex: 'appliedAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'), width: 180,
      render: (_: unknown, record: RefundDto) => (
        record.status === 'PENDING_REVIEW' ? (
          <Space>
            <Button type="link" size="small" onClick={() => openReview(record, 'approve')}>{t('common.approve')}</Button>
            <Button type="link" size="small" danger onClick={() => openReview(record, 'reject')}>{t('common.reject')}</Button>
          </Space>
        ) : null
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder={t('common.status')} allowClear style={{ width: 130 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: t('refund.statusPendingReview'), value: 'PENDING_REVIEW' },
            { label: t('refund.statusApproved'), value: 'APPROVED' },
            { label: t('refund.statusRejected'), value: 'REJECTED' },
            { label: t('refund.statusRefunded'), value: 'REFUNDED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 1000 }} />
      <Modal
        title={reviewModal.action === 'approve' ? t('refund.approveRefund') : t('refund.rejectRefund')}
        open={reviewModal.open}
        onOk={handleReview}
        onCancel={() => setReviewModal({ open: false, refund: null, action: 'approve' })}
        okText={reviewModal.action === 'approve' ? t('common.approve') : t('common.reject')}
        okButtonProps={{ danger: reviewModal.action === 'reject' }}
      >
        <p>{t('refund.refundNo')}：{reviewModal.refund?.refundNo}</p>
        <p>{t('common.amount')}：¥{((reviewModal.refund?.amount || 0) / 100).toFixed(2)}</p>
        <Input.TextArea placeholder={t('refund.reviewNote')} value={reviewNote}
          onChange={(e) => setReviewNote(e.target.value)} rows={3} />
      </Modal>
    </div>
  );
}

export default RefundsPage;
