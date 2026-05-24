import { useEffect, useState, useCallback } from 'react';
import { Table, Button, Space, Tag, Modal, Input, Form, message, Alert } from 'antd';
import { ReloadOutlined, PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import adminApi from '../../api/adminApi';
import type { CreateAdminRequest, ResetPasswordRequest } from '../../api/types';

interface AdminDto {
  userId: number;
  username: string;
  email: string;
  createdAt?: string;
  status?: string;
}

function AdminsPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminApi.listAdmins({ page, size });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('admin.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await adminApi.createAdmin(values as CreateAdminRequest);
      message.success(t('admin.createSuccess'));
      setCreateModalOpen(false);
      form.resetFields();
      fetchData();
    } catch {
      // validation error
    }
  };

  const handleResetPassword = async () => {
    if (!selectedUserId) return;
    try {
      const values = await passwordForm.validateFields();
      await adminApi.resetPassword(selectedUserId, values as ResetPasswordRequest);
      message.success(t('admin.passwordResetSuccess'));
      setPasswordModalOpen(false);
      passwordForm.resetFields();
    } catch {
      // validation error
    }
  };

  const columns = [
    { title: t('common.id'), dataIndex: 'userId', width: 80 },
    { title: t('common.username'), dataIndex: 'username' },
    { title: t('common.email'), dataIndex: 'email', ellipsis: true },
    {
      title: t('common.createdAt'), dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'), width: 160,
      render: (_: unknown, record: AdminDto) => (
        <Space>
          <Button type="link" size="small" onClick={() => {
            setSelectedUserId(record.userId);
            setPasswordModalOpen(true);
          }}>{t('admin.resetPassword')}</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          {t('admin.createAdmin')}
        </Button>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="userId"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 600 }} />

      <Modal title={t('admin.createAdmin')} open={createModalOpen} onOk={handleCreate}
        onCancel={() => { setCreateModalOpen(false); form.resetFields(); }}
        okText={t('common.create')}>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label={t('common.username')} rules={[{ required: true, min: 3, max: 50 }]}>
            <Input placeholder={t('admin.usernamePlaceholder')} />
          </Form.Item>
          <Form.Item name="email" label={t('common.email')} rules={[{ required: true, type: 'email' }]}>
            <Input placeholder={t('admin.emailPlaceholder')} />
          </Form.Item>
          <Form.Item name="password" label={t('admin.passwordLabel')} rules={[{ required: true, min: 8, max: 64 }]}>
            <Input.Password placeholder={t('admin.passwordMinPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={t('admin.resetPassword')} open={passwordModalOpen} onOk={handleResetPassword}
        onCancel={() => { setPasswordModalOpen(false); passwordForm.resetFields(); }}
        okText={t('admin.confirmReset')}>
        <Form form={passwordForm} layout="vertical">
          <Form.Item name="newPassword" label={t('admin.newPassword')} rules={[{ required: true, min: 8, max: 64 }]}>
            <Input.Password placeholder={t('admin.passwordPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default AdminsPage;
