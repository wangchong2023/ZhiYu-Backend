import { useEffect, useState, useCallback } from 'react';
import { Table, Select, Button, Space, Tag, Modal, Input, message, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import subscriptionApi from '../../api/subscriptionApi';
import type { RefundDto, RefundReviewRequest } from '../../api/types';

const statusColor: Record<string, string> = {
  PENDING_REVIEW: 'orange', APPROVED: 'green', REJECTED: 'red', REFUNDED: 'blue',
};

function RefundsPage() {
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
      setError('加载退款列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, size, statusFilter]);

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
      message.success(reviewModal.action === 'approve' ? '已批准退款' : '已拒绝退款');
      setReviewModal({ open: false, refund: null, action: 'approve' });
      setReviewNote('');
      fetchData();
    } catch {
      message.error('操作失败');
    }
  };

  const openReview = (refund: RefundDto, action: 'approve' | 'reject') => {
    setReviewModal({ open: true, refund, action });
    setReviewNote('');
  };

  const columns = [
    { title: '退款单号', dataIndex: 'refundNo', width: 140 },
    { title: '用户名', dataIndex: 'username' },
    { title: '订单号', dataIndex: 'orderNo', width: 140 },
    {
      title: '金额', dataIndex: 'amount', width: 100,
      render: (v: number) => `¥${(v / 100).toFixed(2)}`,
    },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 110,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: '申请时间', dataIndex: 'appliedAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 180,
      render: (_: unknown, record: RefundDto) => (
        record.status === 'PENDING_REVIEW' ? (
          <Space>
            <Button type="link" size="small" onClick={() => openReview(record, 'approve')}>批准</Button>
            <Button type="link" size="small" danger onClick={() => openReview(record, 'reject')}>拒绝</Button>
          </Space>
        ) : null
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder="状态" allowClear style={{ width: 130 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: '待审核', value: 'PENDING_REVIEW' },
            { label: '已批准', value: 'APPROVED' },
            { label: '已拒绝', value: 'REJECTED' },
            { label: '已退款', value: 'REFUNDED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 1000 }} />
      <Modal
        title={reviewModal.action === 'approve' ? '批准退款' : '拒绝退款'}
        open={reviewModal.open}
        onOk={handleReview}
        onCancel={() => setReviewModal({ open: false, refund: null, action: 'approve' })}
        okText={reviewModal.action === 'approve' ? '批准' : '拒绝'}
        okButtonProps={{ danger: reviewModal.action === 'reject' }}
      >
        <p>退款单号：{reviewModal.refund?.refundNo}</p>
        <p>金额：¥{((reviewModal.refund?.amount || 0) / 100).toFixed(2)}</p>
        <Input.TextArea placeholder="审核备注（可选）" value={reviewNote}
          onChange={(e) => setReviewNote(e.target.value)} rows={3} />
      </Modal>
    </div>
  );
}

export default RefundsPage;
