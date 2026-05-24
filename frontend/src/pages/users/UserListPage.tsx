import { useEffect, useState, useCallback } from 'react';
import {
  Table, Input, Select, Button, Space, Tag, Drawer, Descriptions,
  Spin, Alert, Popconfirm, message, TablePaginationConfig,
} from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import apiClient from '../../api/client';
import type { IdentityDto } from '../../api/types';

interface UserDto {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  createdAt: string;
  status: string;
  scope: string;
}

interface UserDetail {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  createdAt: string;
  status: string;
  scope: string;
  recentLogs: Array<{
    username: string;
    action: string;
    result: string;
    ip: string;
    device: string;
    time: string;
  }>;
  identities?: IdentityDto[];
}

const statusColor: Record<string, string> = {
  '正常': 'green', '已禁用': 'red', '已注销': 'default',
};

const providerLabels: Record<string, { label: string; color: string }> = {
  PASSWORD: { label: '密码', color: 'default' },
  WECHAT: { label: '微信', color: 'green' },
  APPLE: { label: 'Apple', color: 'default' },
  GOOGLE: { label: 'Google', color: 'blue' },
  WEBAUTHN: { label: '通行密钥', color: 'purple' },
};

function maskIdentifier(openid: string): string {
  if (!openid) return '-';
  if (openid.length <= 8) return openid;
  return openid.slice(0, 4) + '****' + openid.slice(-4);
}

function UserListPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<UserDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [drawerUser, setDrawerUser] = useState<UserDetail | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (keyword) params.keyword = keyword;
      if (statusFilter) params.status = statusFilter;
      const res = await apiClient.get('/admin/users', { params });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword, statusFilter]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current) setPage(pag.current);
    if (pag.pageSize) setSize(pag.pageSize);
  };

  const handleViewDetail = async (userId: number) => {
    setDrawerLoading(true);
    try {
      const res = await apiClient.get(`/admin/users/${userId}`);
      setDrawerUser(res.data?.data);
    } finally {
      setDrawerLoading(false);
    }
  };

  const handleToggle = async (userId: number, action: 'enable' | 'disable') => {
    await apiClient.post(`/admin/users/${userId}/${action}`);
    message.success(action === 'enable' ? '已启用' : '已禁用');
    fetchUsers();
  };

  const handleUnbind = async (identityId: number) => {
    await apiClient.post(`/user/unbind/${identityId}`);
    message.success('已解绑');
    if (drawerUser) handleViewDetail(drawerUser.userId);
  };

  const columns = [
    { title: 'ID', dataIndex: 'userId', width: 80 },
    { title: '用户名', dataIndex: 'username' },
    { title: '邮箱', dataIndex: 'email', ellipsis: true },
    { title: '手机', dataIndex: 'mobile' },
    { title: '角色', dataIndex: 'scope', width: 80 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    {
      title: '注册时间', dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 200,
      render: (_: unknown, record: UserDto) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleViewDetail(record.userId)}>
            详情
          </Button>
          {record.status === '已禁用' ? (
            <Popconfirm title="确定启用该用户？" onConfirm={() => handleToggle(record.userId, 'enable')}>
              <Button type="link" size="small">启用</Button>
            </Popconfirm>
          ) : record.status === '正常' ? (
            <Popconfirm title="确定禁用该用户？" onConfirm={() => handleToggle(record.userId, 'disable')}>
              <Button type="link" size="small" danger>禁用</Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const identityColumns = [
    {
      title: '类型', dataIndex: 'provider', width: 100,
      render: (v: string) => {
        const info = providerLabels[v] || { label: v, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '标识', dataIndex: 'openid', ellipsis: true,
      render: (v: string) => maskIdentifier(v),
    },
    {
      title: '昵称', dataIndex: 'nickname',
      render: (v: string) => v || '-',
    },
    {
      title: '绑定时间', dataIndex: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 80,
      render: (_: unknown, record: IdentityDto) => {
        if (record.provider === 'PASSWORD') return null;
        return (
          <Popconfirm
            title="确定解绑该认证方式？"
            onConfirm={() => handleUnbind(record.identityId)}
          >
            <Button type="link" size="small" danger>解绑</Button>
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="搜索用户名/邮箱" allowClear style={{ width: 240 }}
          prefix={<SearchOutlined />} value={keyword}
          onChange={(e) => { setKeyword(e.target.value); setPage(1); }} />
        <Select placeholder="状态筛选" allowClear style={{ width: 120 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={[
            { label: '正常', value: 'ENABLED' },
            { label: '已禁用', value: 'DISABLED' },
            { label: '已注销', value: 'DELETED' },
          ]} />
        <Button icon={<ReloadOutlined />} onClick={fetchUsers}>刷新</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchUsers}>重试</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="userId"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 900 }} />

      <Drawer title="用户详情" open={!!drawerUser} onClose={() => setDrawerUser(null)} width={640}
        loading={drawerLoading}>
        {drawerUser && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="用户ID">{drawerUser.userId}</Descriptions.Item>
              <Descriptions.Item label="用户名">{drawerUser.username}</Descriptions.Item>
              <Descriptions.Item label="邮箱">{drawerUser.email || '-'}</Descriptions.Item>
              <Descriptions.Item label="手机">{drawerUser.mobile || '-'}</Descriptions.Item>
              <Descriptions.Item label="角色">{drawerUser.scope}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[drawerUser.status]}>{drawerUser.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="注册时间" span={2}>
                {drawerUser.createdAt ? dayjs(drawerUser.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>
            </Descriptions>

            <h4 style={{ marginTop: 24, marginBottom: 12 }}>认证身份</h4>
            <Table
              dataSource={drawerUser.identities || []}
              rowKey="identityId"
              size="small"
              pagination={false}
              columns={identityColumns}
            />

            <h4 style={{ marginTop: 24, marginBottom: 12 }}>最近登录记录</h4>
            <Table dataSource={drawerUser.recentLogs || []} rowKey={(_, i) => String(i)}
              size="small" pagination={false} columns={[
                { title: '操作', dataIndex: 'action' },
                { title: '结果', dataIndex: 'result', render: (v: string) => (
                  <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v}</Tag>
                )},
                { title: 'IP', dataIndex: 'ip' },
                { title: '时间', dataIndex: 'time', render: (v: string) =>
                    v ? dayjs(v).format('MM-DD HH:mm') : '-' },
              ]} />
          </>
        )}
      </Drawer>
    </div>
  );
}

export default UserListPage;
