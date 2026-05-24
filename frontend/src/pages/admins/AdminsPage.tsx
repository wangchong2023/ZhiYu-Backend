import { useEffect, useState, useCallback } from 'react';
import { Table, Button, Space, Tag, Modal, Input, Form, message, Alert } from 'antd';
import { ReloadOutlined, PlusOutlined } from '@ant-design/icons';
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
      setError('加载管理员列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, size]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await adminApi.createAdmin(values as CreateAdminRequest);
      message.success('管理员创建成功');
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
      message.success('密码已重置');
      setPasswordModalOpen(false);
      passwordForm.resetFields();
    } catch {
      // validation error
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'userId', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '邮箱', dataIndex: 'email', ellipsis: true },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 160,
      render: (_: unknown, record: AdminDto) => (
        <Space>
          <Button type="link" size="small" onClick={() => {
            setSelectedUserId(record.userId);
            setPasswordModalOpen(true);
          }}>重置密码</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          新建管理员
        </Button>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="userId"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 600 }} />

      <Modal title="新建管理员" open={createModalOpen} onOk={handleCreate}
        onCancel={() => { setCreateModalOpen(false); form.resetFields(); }}
        okText="创建">
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, min: 3, max: 50 }]}>
            <Input placeholder="管理员用户名" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email' }]}>
            <Input placeholder="admin@example.com" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 8, max: 64 }]}>
            <Input.Password placeholder="至少8位" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="重置密码" open={passwordModalOpen} onOk={handleResetPassword}
        onCancel={() => { setPasswordModalOpen(false); passwordForm.resetFields(); }}
        okText="确认重置">
        <Form form={passwordForm} layout="vertical">
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 8, max: 64 }]}>
            <Input.Password placeholder="新密码，至少8位" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default AdminsPage;
